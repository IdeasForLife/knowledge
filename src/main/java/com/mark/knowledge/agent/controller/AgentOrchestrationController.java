package com.mark.knowledge.agent.controller;

import com.mark.knowledge.agent.dto.AgentExecutionInfo;
import com.mark.knowledge.agent.service.IntelligentAgentRouter;
import com.mark.knowledge.chat.entity.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 编排控制器
 *
 * 简化版本，不依赖 agentic 模块
 *
 * @author mark
 */
@RestController
@RequestMapping("/api/agent-chat")
public class AgentOrchestrationController {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrationController.class);

    private final IntelligentAgentRouter agentService;

    public AgentOrchestrationController(IntelligentAgentRouter agentService) {
        this.agentService = agentService;
    }

    /**
     * 流式 Agent 聊天接口
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(@RequestBody ChatRequest request) {

        log.info("收到 Agent 聊天请求: message='{}', conversationId={}",
                request.message(), request.conversationId());

        // 使用 Sinks 处理跨线程的 SSE 发送
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().multicast().onBackpressureBuffer();

        // 在异步线程中处理
        agentService.chatAsync(
                request.message(),
                request.conversationId(),
                // onChunk
                chunk -> {
                    log.debug("发送消息块: {}", chunk);
                    Sinks.EmitResult result = sink.tryEmitNext(ServerSentEvent.<String>builder()
                            .event("message")
                            .data(chunk)
                            .build());
                    if (result != Sinks.EmitResult.OK) {
                        log.warn("发送消息块失败: {}", result);
                    }
                },
                // onComplete
                conversationId -> {
                    log.info("Agent 对话完成, conversationId={}", conversationId);

                    // 发送 Agent 执行历史
                    List<AgentExecutionInfo> history = agentService.getExecutionHistory();
                    String historyJson = toJson(history);
                    sink.tryEmitNext(ServerSentEvent.<String>builder()
                            .event("agent-history")
                            .data(historyJson)
                            .build());

                    sink.tryEmitNext(ServerSentEvent.<String>builder()
                            .event("done")
                            .data(conversationId)
                            .build());
                    sink.tryEmitComplete();
                },
                // onError
                error -> {
                    log.error("Agent stream chat error", error);
                    sink.tryEmitError(error);
                }
        );

        return sink.asFlux();
    }

    /**
     * 获取聊天历史
     */
    @GetMapping("/history/{conversationId}")
    public List<ChatMessage> getHistory(@PathVariable String conversationId) {
        log.info("获取 Agent 对话历史: {}", conversationId);
        return agentService.getChatHistory(conversationId);
    }

    /**
     * 获取所有会话列表
     */
    @GetMapping("/conversations")
    public List<String> getAllConversations() {
        log.info("获取所有 Agent 对话列表");
        return agentService.getAllConversationIds();
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/conversations/{conversationId}")
    public void deleteConversation(@PathVariable String conversationId) {
        log.info("删除 Agent 对话: {}", conversationId);
        agentService.deleteConversation(conversationId);
    }

    /**
     * 获取最近的 Agent 执行历史
     */
    @GetMapping("/agent-execution-history")
    public List<AgentExecutionInfo> getAgentExecutionHistory() {
        log.info("获取 Agent 执行历史");
        return agentService.getExecutionHistory();
    }

    /**
     * 清空 Agent 执行历史
     */
    @DeleteMapping("/agent-execution-history")
    public void clearAgentExecutionHistory() {
        log.info("清空 Agent 执行历史");
        agentService.clearExecutionHistory();
    }

    /**
     * 测试工具调用的接口
     */
    @PostMapping("/test-tool")
    public ToolTestResult testTool(@RequestBody ToolTestRequest request) {
        log.info("测试工具调用: type={}, input={}", request.type(), request.input());

        return switch (request.type()) {
            case "calculation" -> {
                String result = agentService.testCalculation(request.input());
                yield new ToolTestResult("calculation", result);
            }
            case "weather" -> {
                String result = agentService.testWeather(request.input());
                yield new ToolTestResult("weather", result);
            }
            case "time" -> {
                String result = agentService.testTime(request.input());
                yield new ToolTestResult("time", result);
            }
            default -> new ToolTestResult("unknown", "未知工具类型");
        };
    }

    public record ToolTestRequest(String type, String input) {}
    public record ToolTestResult(String type, String result) {}

    private String toJson(List<AgentExecutionInfo> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        return list.stream()
                .map(item -> String.format(
                        "{\"step\":%d,\"agentName\":\"%s\",\"status\":\"%s\",\"duration\":%s,\"output\":\"%s\"}",
                        item.step(), item.agentName(), item.status(),
                        item.duration() != null ? item.duration() : "null",
                        item.output() != null ? item.output().replace("\"", "\\\"") : ""
                ))
                .collect(Collectors.joining(",", "[", "]"));
    }

    /**
     * 聊天请求
     */
    public record ChatRequest(
            String message,
            String conversationId
    ) {}
}
