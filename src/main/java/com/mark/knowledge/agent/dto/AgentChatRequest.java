package com.mark.knowledge.agent.dto;

/**
 * Agent 聊天请求
 *
 * @author mark
 */
public record AgentChatRequest(
        String message,
        String conversationId,
        Boolean enableVectorStore,
        Boolean enableMcp,
        Boolean enableTools,
        Integer contextWindowSize
) {
    public AgentChatRequest {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Message cannot be null or empty");
        }
    }

    public static AgentChatRequest of(String message, String conversationId) {
        return new AgentChatRequest(message, conversationId, null, null, null, null);
    }
}
