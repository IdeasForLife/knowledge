package com.mark.knowledge.agent.dto;

import java.util.List;

/**
 * Agent 执行上下文
 *
 * @author mark
 */
public record AgentExecutionContext(
        String conversationId,
        List<ContextMessage> contextHistory,
        String vectorStoreContext,
        String mcpFileContext
) {
    /**
     * 上下文消息
     */
    public record ContextMessage(
            String role,
            String content,
            long timestamp
    ) {}
}
