package com.mark.knowledge.chat.service;

import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 模型路由服务
 *
 * 根据配置的策略（百分比或业务类型）路由请求到不同的模型：
 * - 阿里云DashScope模型（云端）
 * - 本地Ollama模型
 *
 * @author mark
 */
@Service
public class ModelRouterService {

    private static final Logger log = LoggerFactory.getLogger(ModelRouterService.class);

    @Autowired
    private ChatModel ollamaChatModel;

    @Autowired(required = false)
    private ChatModel dashscopeChatModel;

    @Value("${model-router.strategy:PERCENTAGE}")
    private String routerStrategy;

    @Value("${model-router.percentage.aliyun:30}")
    private int aliyunPercentage;

    @Value("${model-router.percentage.local:70}")
    private int localPercentage;

    @Value("${model-router.business-type.aliyun-types:COMPLEX_QUERY,LONG_CONTEXT,HIGH_PRECISION}")
    private List<String> aliyunBusinessTypes;

    @Value("${model-router.business-type.local-types:SIMPLE_QA,TOOL_CALLING,GENERAL_CHAT}")
    private List<String> localBusinessTypes;

    private final Random random = new Random();

    /**
     * �由策略枚举
     */
    public enum RouterStrategy {
        PERCENTAGE,      // 百分比路由
        BUSINESS_TYPE    // 业务类型路由
    }

    /**
     * 业务类型枚举
     */
    public enum BusinessType {
        COMPLEX_QUERY,    // 复杂查询
        LONG_CONTEXT,     // 长上下文
        HIGH_PRECISION,   // 高精度要求
        SIMPLE_QA,        // 简单问答
        TOOL_CALLING,     // 工具调用
        GENERAL_CHAT      // 通用对话
    }

    /**
     * 根据策略选择合适的ChatModel
     *
     * @param businessType 业务类型（可选，用于BUSINESS_TYPE策略）
     * @return ChatModel实例
     */
    public ChatModel routeModel(BusinessType businessType) {
        RouterStrategy strategy = RouterStrategy.valueOf(routerStrategy);

        switch (strategy) {
            case PERCENTAGE:
                return routeByPercentage();

            case BUSINESS_TYPE:
                if (businessType != null) {
                    return routeByBusinessType(businessType);
                }
                log.warn("未指定业务类型，使用默认百分比路由");
                return routeByPercentage();

            default:
                log.warn("未知的路由策略: {}，使用本地模型", routerStrategy);
                return ollamaChatModel;
        }
    }

    /**
     * 根据百分比选择模型
     */
    private ChatModel routeByPercentage() {
        // 如果没有配置阿里云模型，直接使用本地模型
        if (dashscopeChatModel == null) {
            log.debug("阿里云模型未配置，使用本地模型");
            return ollamaChatModel;
        }

        // 生成0-100的随机数
        int rand = random.nextInt(100);

        if (rand < aliyunPercentage) {
            log.debug("路由到阿里云模型 (随机值: {} < {}%)", rand, aliyunPercentage);
            return dashscopeChatModel;
        } else {
            log.debug("路由到本地模型 (随机值: {} >= {}%)", rand, aliyunPercentage);
            return ollamaChatModel;
        }
    }

    /**
     * 根据业务类型选择模型
     */
    private ChatModel routeByBusinessType(BusinessType businessType) {
        // 如果没有配置阿里云模型，直接使用本地模型
        if (dashscopeChatModel == null) {
            log.debug("阿里云模型未配置，使用本地模型 (业务类型: {})", businessType);
            return ollamaChatModel;
        }

        String businessTypeName = businessType.name();

        if (aliyunBusinessTypes != null && aliyunBusinessTypes.contains(businessTypeName)) {
            log.debug("业务类型 {} 路由到阿里云模型", businessTypeName);
            return dashscopeChatModel;
        } else if (localBusinessTypes != null && localBusinessTypes.contains(businessTypeName)) {
            log.debug("业务类型 {} 路由到本地模型", businessTypeName);
            return ollamaChatModel;
        } else {
            log.debug("业务类型 {} 未配置路由，使用本地模型", businessTypeName);
            return ollamaChatModel;
        }
    }

    /**
     * 智能判断业务类型
     * 根据用户输入内容自动判断业务类型
     *
     * @param userInput 用户输入
     * @return 业务类型
     */
    public BusinessType detectBusinessType(String userInput) {
        if (userInput == null || userInput.isEmpty()) {
            return BusinessType.GENERAL_CHAT;
        }

        // 检测复杂查询
        if (isComplexQuery(userInput)) {
            return BusinessType.COMPLEX_QUERY;
        }

        // 检测工具调用（包含"计算"、"分析"等关键词）
        if (isToolCalling(userInput)) {
            return BusinessType.TOOL_CALLING;
        }

        // 检测长上下文需求
        if (userInput.length() > 200) {
            return BusinessType.LONG_CONTEXT;
        }

        // 默认为简单问答
        return BusinessType.SIMPLE_QA;
    }

    /**
     * 判断是否为复杂查询
     */
    private boolean isComplexQuery(String input) {
        String[] complexKeywords = {
            "分析", "比较", "总结", "详细说明", "深入",
            "推理", "判断", "评估", "建议", "方案"
        };

        for (String keyword : complexKeywords) {
            if (input.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否为工具调用场景
     */
    private boolean isToolCalling(String input) {
        String[] toolKeywords = {
            "计算", "查询", "搜索", "天气", "时间",
            "IRR", "NPV", "债券", "期权", "摊销"
        };

        for (String keyword : toolKeywords) {
            if (input.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
