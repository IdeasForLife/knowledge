package com.mark.knowledge.chat.dto;

/**
 * 聊天请求 DTO
 */
public record ChatRequest(
    String question,
    String conversationId
) {
    // 确保字段不为 null
    public ChatRequest {
        if (question == null) {
            question = "";
        }
        if (conversationId == null) {
            conversationId = null;
        }
    }
}
