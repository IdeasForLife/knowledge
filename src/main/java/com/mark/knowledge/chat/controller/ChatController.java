package com.mark.knowledge.chat.controller;

import com.mark.knowledge.chat.dto.ChatRequest;
import com.mark.knowledge.chat.entity.ChatMessage;
import com.mark.knowledge.chat.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 聊天控制器 - 支持流式响应和对话历史管理
 *
 * @author mark
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 流式聊天接口
     *
     * @param request 聊天请求（包含问题和对话ID）
     * @return 流式响应
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(@RequestBody ChatRequest request) {

        log.info("收到流式聊天请求: question='{}', conversationId={}", request.question(), request.conversationId());

        return Flux.create(sink -> {
            chatService.chatAsync(
                request.question(),
                request.conversationId(),
                // onChunk - 每收到一个token就发送
                chunk -> {
                    sink.next(ServerSentEvent.<String>builder()
                        .data(chunk)
                        .event("message")
                        .build());
                },
                // onComplete - 完成时发送会话ID
                finalConversationId -> {
                    sink.next(ServerSentEvent.<String>builder()
                        .data(finalConversationId)
                        .event("done")
                        .build());
                    sink.complete();
                    log.info("对话流式响应完成: {}", finalConversationId);
                },
                // onError - 错误处理
                error -> {
                    log.error("Stream chat error", error);
                    sink.error(error);
                }
            );
        });
    }

    /**
     * 获取聊天历史
     *
     * @param conversationId 对话ID
     * @return 聊天历史消息列表
     */
    @GetMapping("/history/{conversationId}")
    public List<ChatMessage> getHistory(@PathVariable String conversationId) {
        log.info("获取对话历史: {}", conversationId);
        return chatService.getChatHistory(conversationId);
    }

    /**
     * 获取所有会话列表
     *
     * @return 对话ID列表
     */
    @GetMapping("/conversations")
    public List<String> getAllConversations() {
        log.info("获取所有对话列表");
        return chatService.getAllConversationIds();
    }

    /**
     * 删除会话
     *
     * @param conversationId 对话ID
     */
    @DeleteMapping("/conversations/{conversationId}")
    public void deleteConversation(@PathVariable String conversationId) {
        log.info("删除对话: {}", conversationId);
        chatService.deleteConversation(conversationId);
    }

    /**
     * 清空所有聊天记录
     */
    @DeleteMapping("/history")
    public void clearHistory() {
        log.info("清空所有聊天历史");
        chatService.clearAllHistory();
    }
}
