package com.mark.knowledge.agent.dto;

/**
 * Agent 执行信息
 *
 * @author mark
 */
public record AgentExecutionInfo(
        int step,
        String agentName,
        String status,
        Long duration,
        String output
) {}
