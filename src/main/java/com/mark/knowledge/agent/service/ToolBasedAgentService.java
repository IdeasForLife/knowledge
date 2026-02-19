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
 * 基于 @Tool 注解的 Agent 服务
 *
 * 使用 LangChain4j 标准的工具调用方式
 * LLM 会自动决定何时调用哪个工具
 *
 * @author mark
 */
@Service
public class ToolBasedAgentService {

    private static final Logger log = LoggerFactory.getLogger(ToolBasedAgentService.class);

    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ChatMessageRepository chatMessageRepository;
    private final AgentConfig config;
    private final ToolAgent toolAgent;

    private final List<AgentExecutionInfo> executionHistory = new CopyOnWriteArrayList<>();

    // 带工具的 Agent - LLM 会自动调用 @Tool 标记的方法
    private final AgentWithTools agentWithTools;

    public ToolBasedAgentService(
            ChatModel chatModel,
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            ChatMessageRepository chatMessageRepository,
            AgentConfig config,
            ToolAgent toolAgent) {
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.chatMessageRepository = chatMessageRepository;
        this.config = config;
        this.toolAgent = toolAgent;

        // 创建带工具的 Agent
        this.agentWithTools = AiServices.builder(AgentWithTools.class)
                .chatModel(chatModel)
                .tools(toolAgent)  // 注册工具
                .build();
    }

    /**
     * 异步聊天 - 使用 @Tool 注解方式
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

        String finalConversationId = conversationId != null ? conversationId : UUID.randomUUID().toString();

        // 保存用户消息
        ChatMessage userMessage = new ChatMessage(finalConversationId, "user", message, null);
        chatMessageRepository.save(userMessage);

        executionHistory.clear();

        long startTime = System.currentTimeMillis();

        // 构建上下文
        String context = buildContext(finalConversationId, message);

        // 查询向量数据库
        if (config.isVectorStoreEnabled()) {
            addExecutionEvent(1, "VectorStore", "STARTED", null, "搜索知识库...");
            String vectorContext = searchVectorStore(message);
            if (!vectorContext.isEmpty()) {
                context += "\n\n=== 相关文档 ===\n" + vectorContext;
                addExecutionEvent(1, "VectorStore", "COMPLETED",
                        System.currentTimeMillis(), "找到相关文档");
            } else {
                addExecutionEvent(1, "VectorStore", "COMPLETED",
                        System.currentTimeMillis(), "未找到相关文档");
            }
        }

        // LLM 自动决定是否调用工具
        addExecutionEvent(2, "LLM_Agent", "STARTED", null, "AI 正在思考，可能会调用工具...");

        String enhancedPrompt = buildPrompt(context, message);
        String response = agentWithTools.chat(enhancedPrompt);

        addExecutionEvent(2, "LLM_Agent", "COMPLETED",
                System.currentTimeMillis() - startTime, null);

        // 流式输出
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

    private String buildPrompt(String context, String message) {
        return """
                你是一个智能 AI 助手，具有以下能力：
                1. 可以基于提供的知识库内容回答问题
                2. 可以使用工具进行计算、查询天气、获取时间等

                %s

                === 用户问题 ===
                %s

                请回答用户的问题。如果需要计算或查询信息，请使用相应的工具。
                如果知识库中有相关信息，请优先使用知识库内容回答。
                """.formatted(context.isEmpty() ? "（暂无上下文）" : context, message);
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
     * 带工具的 Agent 接口
     *
     * LLM 会自动调用工具方法
     */
    public interface AgentWithTools {
        String chat(String message);
    }
}
