package com.mark.knowledge.agent.controller;

import com.mark.knowledge.agent.dto.AgentExecutionInfo;
import com.mark.knowledge.agent.service.AgenticService;
import com.mark.knowledge.chat.entity.ChatMessage;
import com.mark.knowledge.chat.service.AuthService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 编排控制器
 *
 * 使用 LangChain4j agentic 模块重构
 *
 * @author mark
 */
@RestController
@RequestMapping("/api/agent-chat")
public class AgentOrchestrationController {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrationController.class);
    private static final Long MARK_USER_ID = 1L;

    private final AgenticService agenticService;
    private final AuthService authService;

    public AgentOrchestrationController(
            AgenticService agenticService,
            AuthService authService) {
        this.agenticService = agenticService;
        this.authService = authService;
    }

    /**
     * 获取当前登录用户ID（辅助方法）
     */
    private Long getCurrentUserId(HttpSession session) {
        Long userId = authService.getCurrentUserId(session);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return userId;
    }

    /**
     * 流式 Agent 聊天接口
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(@RequestBody ChatRequest request, HttpSession session) {

        // 验证登录
        Long userId = getCurrentUserId(session);

        log.info("收到 Agent 聊天请求: message='{}', conversationId={}, userId={}",
                request.message(), request.conversationId(), userId);

        // 使用 Sinks 处理跨线程的 SSE 发送
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().multicast().onBackpressureBuffer();

        // 在异步线程中处理 - 使用新的AgenticService
        agenticService.chatAsync(
                request.message(),
                request.conversationId(),
                userId,
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
                    List<AgentExecutionInfo> history = agenticService.getExecutionHistory();
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
        return agenticService.getChatHistory(conversationId);
    }

    /**
     * 获取所有会话列表
     */
    @GetMapping("/conversations")
    public List<String> getAllConversations(HttpSession session) {
        Long userId = getCurrentUserId(session);
        log.info("获取用户 {} 的 Agent 对话列表", userId);
        return agenticService.getAllConversationIds(userId);
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/conversations/{conversationId}")
    public void deleteConversation(@PathVariable String conversationId, HttpSession session) {
        Long userId = getCurrentUserId(session);
        log.info("用户 {} 删除 Agent 对话: {}", userId, conversationId);
        agenticService.deleteConversation(conversationId);
    }

    /**
     * 获取最近的 Agent 执行历史
     */
    @GetMapping("/agent-execution-history")
    public List<AgentExecutionInfo> getAgentExecutionHistory() {
        log.info("获取 Agent 执行历史");
        return agenticService.getExecutionHistory();
    }

    /**
     * 清空 Agent 执行历史
     */
    @DeleteMapping("/agent-execution-history")
    public void clearAgentExecutionHistory() {
        log.info("清空 Agent 执行历史");
        agenticService.clearExecutionHistory();
    }

    /**
     * 测试工具调用的接口
     */
    @PostMapping("/test-tool")
    public ToolTestResult testTool(@RequestBody ToolTestRequest request) {
        log.info("测试工具调用: type={}, input={}", request.type(), request.input());

        return switch (request.type()) {
            case "calculation" -> {
                String result = agenticService.testCalculation(request.input());
                yield new ToolTestResult("calculation", result);
            }
            case "weather" -> {
                String result = agenticService.testWeather(request.input());
                yield new ToolTestResult("weather", result);
            }
            case "time" -> {
                String result = agenticService.testTime(request.input());
                yield new ToolTestResult("time", result);
            }
            default -> new ToolTestResult("unknown", "未知工具类型");
        };
    }

    public record ToolTestRequest(String type, String input) {}
    public record ToolTestResult(String type, String result) {}

    /**
     * 转换为JSON - 支持两种类型的AgentExecutionInfo
     */
    private String toJson(List<?> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        return list.stream()
                .map(item -> {
                    // 使用反射获取字段值
                    try {
                        int step = (int) item.getClass().getMethod("step").invoke(item);
                        String agentName = (String) item.getClass().getMethod("agentName").invoke(item);
                        String status = (String) item.getClass().getMethod("status").invoke(item);
                        Object duration = item.getClass().getMethod("duration").invoke(item);
                        Object output = item.getClass().getMethod("output").invoke(item);

                        return String.format(
                                "{\"step\":%d,\"agentName\":\"%s\",\"status\":\"%s\",\"duration\":%s,\"output\":\"%s\"}",
                                step, agentName, status,
                                duration != null ? duration : "null",
                                output != null ? output.toString().replace("\"", "\\\"") : ""
                        );
                    } catch (Exception e) {
                        return "{}";
                    }
                })
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
