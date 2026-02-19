package com.mark.knowledge.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 请求分类器
 *
 * 使用 LLM 和规则相结合的方式分类用户请求
 *
 * @author mark
 */
@Component
public class RequestClassifier {

    private static final Logger log = LoggerFactory.getLogger(RequestClassifier.class);

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    // 规则模式
    private static final Pattern CALCULATION_PATTERN = Pattern.compile(
            ".*?([0-9]+\\.?[0-9]*\\s*[+\\-*/^%]\\s*[0-9]+\\.?[0-9]*).*|" +
            ".*?(计算|算|等于|求|\\=).*?([0-9]+).*?([+\\-*/^%]).*?([0-9]+).*"
    );

    private static final Pattern WEATHER_PATTERN = Pattern.compile(
            ".*?(天气|气温|温度|下雨|下雪|晴天|阴天).*?" +
            ".*?(北京|上海|广州|深圳|杭州|苏州|南京|武汉|成都|重庆|西安|天津|青岛|大连|厦门|长沙|郑州).*"
    );

    private static final Pattern TIME_PATTERN = Pattern.compile(
            ".*?(现在|当前|几点|什么时间|今天|明天|昨天).*"
    );

    public RequestClassifier(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    /**
     * 分类请求类型
     */
    public RequestType classify(String message) {
        // 先使用规则快速匹配
        RequestType ruleBasedType = classifyByRules(message);
        if (ruleBasedType != RequestType.GENERAL) {
            log.info("Rule-based classification: {} -> {}", message, ruleBasedType);
            return ruleBasedType;
        }

        // 使用 LLM 进行更精确的分类
        return classifyByLLM(message);
    }

    /**
     * 基于规则的分类
     */
    private RequestType classifyByRules(String message) {
        String lowerMessage = message.toLowerCase();

        // 计算类问题
        if (CALCULATION_PATTERN.matcher(lowerMessage).matches()) {
            return RequestType.CALCULATION;
        }

        // 天气类问题
        if (WEATHER_PATTERN.matcher(message).matches()) {
            return RequestType.WEATHER;
        }

        // 时间类问题
        if (TIME_PATTERN.matcher(message).matches()) {
            return RequestType.TIME;
        }

        return RequestType.GENERAL;
    }

    /**
     * 基于 LLM 的分类
     */
    private RequestType classifyByLLM(String message) {
        String classificationPrompt = String.format("""
                请分析以下用户请求的类型，只返回类型名称（KNOWLEDGE、CALCULATION、WEATHER、TIME、GENERAL）：

                用户请求：%s

                类型说明：
                - KNOWLEDGE: 需要从知识库中查找信息的查询
                - CALCULATION: 数学计算问题
                - WEATHER: 天气查询
                - TIME: 时间或日期相关查询
                - GENERAL: 一般性对话或其他问题

                只返回类型名称，不要有其他内容。
                """, message);

        try {
            String response = chatModel.chat(classificationPrompt).trim().toUpperCase();

            // 尝试解析响应
            for (RequestType type : RequestType.values()) {
                if (response.contains(type.name())) {
                    log.info("LLM classification: {} -> {}", message, type);
                    return type;
                }
            }
        } catch (Exception e) {
            log.error("LLM classification failed, falling back to GENERAL", e);
        }

        return RequestType.GENERAL;
    }

    /**
     * 请求类型枚举
     */
    public enum RequestType {
        KNOWLEDGE("知识库查询"),
        CALCULATION("计算"),
        WEATHER("天气查询"),
        TIME("时间查询"),
        GENERAL("一般对话");

        private final String description;

        RequestType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
