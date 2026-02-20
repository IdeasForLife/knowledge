package com.mark.knowledge.chat.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云DashScope配置类
 *
 * 使用OpenAI兼容接口连接阿里云DashScope
 *
 * @author mark
 */
@Configuration
@ConditionalOnProperty(name = "dashscope.api-key", matchIfMissing = false)
public class DashScopeConfig {

    private static final Logger log = LoggerFactory.getLogger(DashScopeConfig.class);

    @Value("${dashscope.api-key}")
    private String apiKey;

    @Value("${dashscope.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String baseUrl;

    @Value("${dashscope.model:qwen-plus}")
    private String modelName;

    @Value("${dashscope.timeout:60s}")
    private String timeout;

    /**
     * 创建阿里云DashScope聊天模型Bean
     * 使用OpenAI兼容接口
     */
    @Bean
    public ChatModel dashscopeChatModel() {
        // 检查API key是否为默认值
        if ("your-api-key-here".equals(apiKey)) {
            log.error("==========================================");
            log.error("⚠️  阿里云DashScope API Key未配置！");
            log.error("  请在application.yaml中设置真实API Key");
            log.error("  或设置环境变量: DASHSCOPE_API_KEY");
            log.error("==========================================");
        }

        log.info("==========================================");
        log.info("✅ 初始化阿里云DashScope聊天模型");
        log.info("  模型: {}", modelName);
        log.info("  URL: {}", baseUrl);
        log.info("  API Key: {}...", apiKey.substring(0, Math.min(10, apiKey.length())));
        log.info("==========================================");

        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.7)
                .maxTokens(2000)
                .timeout(parseTimeout(timeout))
                .build();
    }

    /**
     * 解析超时时间字符串为Duration对象
     */
    private java.time.Duration parseTimeout(String timeout) {
        try {
            if (timeout.endsWith("s")) {
                long seconds = Long.parseLong(timeout.substring(0, timeout.length() - 1));
                return java.time.Duration.ofSeconds(seconds);
            }
            return java.time.Duration.ofSeconds(60);
        } catch (Exception e) {
            return java.time.Duration.ofSeconds(60);
        }
    }
}
