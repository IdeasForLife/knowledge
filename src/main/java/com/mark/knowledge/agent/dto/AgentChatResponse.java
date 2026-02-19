package com.mark.knowledge.agent.dto;

import java.util.List;

/**
 * Agent 聊天响应
 *
 * @author mark
 */
public record AgentChatResponse(
        String response,
        String conversationId,
        List<AgentExecutionInfo> agentExecutionHistory,
        String sources
) {
    public AgentChatResponse {
        if (response == null) {
            response = "";
        }
    }

    public static AgentChatResponse of(String response, String conversationId) {
        return new AgentChatResponse(response, conversationId, List.of(), null);
    }

    /**
     * Agent 执行信息
     */
    public record AgentExecutionInfo(
            int step,
            String agentName,
            String status,
            Long duration,
            String output
    ) {}
}
