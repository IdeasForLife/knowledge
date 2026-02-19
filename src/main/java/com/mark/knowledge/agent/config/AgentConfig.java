package com.mark.knowledge.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 智能体配置类
 *
 * @author mark
 */
@Configuration
@ConfigurationProperties(prefix = "agent")
public class AgentConfig {

    /**
     * 上下文滑动窗口大小（保留最近N条消息）
     */
    private int contextWindowSize = 10;

    /**
     * 是否启用向量数据库查询
     */
    private boolean vectorStoreEnabled = true;

    /**
     * 是否启用 MCP 文件操作
     */
    private boolean mcpFileEnabled = true;

    /**
     * 是否启用工具调用
     */
    private boolean toolCallEnabled = true;

    /**
     * 向量数据库最大结果数
     */
    private int vectorMaxResults = 5;

    /**
     * 向量数据库最小相似度分数
     */
    private double vectorMinScore = 0.5;

    /**
     * MCP 允许访问的目录
     */
    private String mcpAllowedDirectory = System.getProperty("user.dir");

    /**
     * Agent 执行超时时间（秒）
     */
    private int agentTimeoutSeconds = 120;

    public int getContextWindowSize() {
        return contextWindowSize;
    }

    public void setContextWindowSize(int contextWindowSize) {
        this.contextWindowSize = contextWindowSize;
    }

    public boolean isVectorStoreEnabled() {
        return vectorStoreEnabled;
    }

    public void setVectorStoreEnabled(boolean vectorStoreEnabled) {
        this.vectorStoreEnabled = vectorStoreEnabled;
    }

    public boolean isMcpFileEnabled() {
        return mcpFileEnabled;
    }

    public void setMcpFileEnabled(boolean mcpFileEnabled) {
        this.mcpFileEnabled = mcpFileEnabled;
    }

    public boolean isToolCallEnabled() {
        return toolCallEnabled;
    }

    public void setToolCallEnabled(boolean toolCallEnabled) {
        this.toolCallEnabled = toolCallEnabled;
    }

    public int getVectorMaxResults() {
        return vectorMaxResults;
    }

    public void setVectorMaxResults(int vectorMaxResults) {
        this.vectorMaxResults = vectorMaxResults;
    }

    public double getVectorMinScore() {
        return vectorMinScore;
    }

    public void setVectorMinScore(double vectorMinScore) {
        this.vectorMinScore = vectorMinScore;
    }

    public String getMcpAllowedDirectory() {
        return mcpAllowedDirectory;
    }

    public void setMcpAllowedDirectory(String mcpAllowedDirectory) {
        this.mcpAllowedDirectory = mcpAllowedDirectory;
    }

    public int getAgentTimeoutSeconds() {
        return agentTimeoutSeconds;
    }

    public void setAgentTimeoutSeconds(int agentTimeoutSeconds) {
        this.agentTimeoutSeconds = agentTimeoutSeconds;
    }
}
