package com.mark.knowledge.agent.service;

import com.mark.knowledge.agent.config.AgentConfig;
import com.mark.knowledge.agent.dto.AgentExecutionInfo;
import com.mark.knowledge.chat.entity.ChatMessage;
import com.mark.knowledge.chat.repository.ChatMessageRepository;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Agent 编排服务
 *
 * 简化版本，不依赖 agentic 模块
 *
 * @author mark
 */
@Service
public class AgentOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrationService.class);

    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ChatMessageRepository chatMessageRepository;
    private final AgentConfig config;
    private final List<AgentExecutionInfo> executionHistory = new CopyOnWriteArrayList<>();

    private final GeneralAgent generalAgent;

    public AgentOrchestrationService(
            ChatModel chatModel,
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            ChatMessageRepository chatMessageRepository,
            AgentConfig config) {
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.chatMessageRepository = chatMessageRepository;
        this.config = config;

        // 创建通用 Agent
        this.generalAgent = AiServices.builder(GeneralAgent.class)
                .chatModel(chatModel)
                .build();
    }

    /**
     * 异步聊天 - 支持流式回调
     */
    @Transactional
    public void chatAsync(
            String message,
            String conversationId,
            Consumer<String> onChunk,
            Consumer<String> onComplete,
            Consumer<Exception> onError) {

        CompletableFuture.runAsync(() -> {
            try {
                processChat(message, conversationId, onChunk, onComplete);
            } catch (Exception e) {
                log.error("Chat failed", e);
                onError.accept(e);
            }
        });
    }

    private void processChat(
            String message,
            String conversationId,
            Consumer<String> onChunk,
            Consumer<String> onComplete) {

        // 创建或获取会话 ID
        String finalConversationId = conversationId != null ? conversationId : UUID.randomUUID().toString();

        // 保存用户消息
        ChatMessage userMessage = new ChatMessage(finalConversationId, "user", message, null);
        chatMessageRepository.save(userMessage);

        // 清空执行历史
        executionHistory.clear();

        // 构建上下文
        String context = buildContext(finalConversationId, message);

        // 执行 Agent
        long startTime = System.currentTimeMillis();

        // 记录向量检索
        if (config.isVectorStoreEnabled()) {
            addExecutionEvent(1, "VectorStore", "STARTED", null, null);
            String vectorContext = searchVectorStore(message);
            if (!vectorContext.isEmpty()) {
                context += "\n\n=== 相关文档 ===\n" + vectorContext;
                addExecutionEvent(1, "VectorStore", "COMPLETED",
                        System.currentTimeMillis() - startTime, "找到相关文档");
            } else {
                addExecutionEvent(1, "VectorStore", "COMPLETED",
                        System.currentTimeMillis() - startTime, "未找到相关文档");
            }
        }

        // 记录通用对话
        addExecutionEvent(2, "GeneralChat", "STARTED", null, null);

        String enhancedPrompt = buildEnhancedPrompt(context, message);
        String response = generalAgent.chat(enhancedPrompt);

        addExecutionEvent(2, "GeneralChat", "COMPLETED",
                System.currentTimeMillis() - startTime, null);

        // 模拟流式输出
        simulateStreamOutput(response, onChunk);

        // 保存助手消息
        ChatMessage assistantMessage = new ChatMessage(
                finalConversationId,
                "assistant",
                response,
                null
        );
        chatMessageRepository.save(assistantMessage);

        onComplete.accept(finalConversationId);
    }

    private String buildContext(String conversationId, String message) {
        int windowSize = config.getContextWindowSize();
        List<ChatMessage> history = chatMessageRepository
                .findLastNMessagesByConversationId(conversationId, windowSize);

        StringBuilder context = new StringBuilder();

        if (!history.isEmpty()) {
            context.append("=== 对话历史 ===\n");
            for (ChatMessage msg : history) {
                context.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }
            context.append("\n");
        }

        return context.toString();
    }

    private String searchVectorStore(String question) {
        try {
            var questionEmbedding = embeddingModel.embed(question).content();

            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(questionEmbedding)
                    .maxResults(config.getVectorMaxResults())
                    .minScore(config.getVectorMinScore())
                    .build();

            EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
            List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

            if (matches.isEmpty()) {
                return "";
            }

            return matches.stream()
                    .map(match -> {
                        TextSegment segment = match.embedded();
                        String filename = segment.metadata() != null
                                ? segment.metadata().getString("filename")
                                : "未知文件";
                        return String.format("[%s (相似度: %.2f%%)]\n%s",
                                filename, match.score() * 100, segment.text());
                    })
                    .collect(Collectors.joining("\n\n---\n\n"));

        } catch (Exception e) {
            log.error("Vector search failed", e);
            return "";
        }
    }

    private String buildEnhancedPrompt(String context, String message) {
        return """
                你是一个友好的 AI 助手，可以帮助用户回答问题和完成任务。
                %s
                === 用户问题 ===
                %s
                
                请基于提供的上下文（如果有）回答用户的问题。如果上下文中没有相关信息，请诚实地告知用户。
                """.formatted(context.isEmpty() ? "" : context, message);
    }

    private void simulateStreamOutput(String text, Consumer<String> onChunk) {
        String[] sentences = text.split("(?<=[.!?。！？\\n])");
        for (String sentence : sentences) {
            if (!sentence.trim().isEmpty()) {
                onChunk.accept(sentence);
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void addExecutionEvent(int step, String agentName, String status,
                                   Long duration, String output) {
        AgentExecutionInfo event = new AgentExecutionInfo(step, agentName, status, duration, output);
        executionHistory.add(event);
    }

    public List<AgentExecutionInfo> getExecutionHistory() {
        return new ArrayList<>(executionHistory);
    }

    public void clearExecutionHistory() {
        executionHistory.clear();
    }

    public List<ChatMessage> getChatHistory(String conversationId) {
        return chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    public List<String> getAllConversationIds() {
        return chatMessageRepository.findAllConversationIds();
    }

    @Transactional
    public void deleteConversation(String conversationId) {
        chatMessageRepository.deleteByConversationId(conversationId);
        log.info("Deleted conversation: {}", conversationId);
    }

    /**
     * 通用 Agent 接口
     */
    public interface GeneralAgent {
        String chat(String message);
    }
}
