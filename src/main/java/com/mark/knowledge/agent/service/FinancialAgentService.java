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
 * 金融智能体服务
 *
 * 整合金融计算工具和通用对话能力
 *
 * @author mark
 */
@Service
public class FinancialAgentService {

    private static final Logger log = LoggerFactory.getLogger(FinancialAgentService.class);

    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ChatMessageRepository chatMessageRepository;
    private final AgentConfig config;
    private final FinancialToolAgent financialToolAgent;

    private final List<AgentExecutionInfo> executionHistory = new CopyOnWriteArrayList<>();

    private final FinancialAgent financialAgent;

    public FinancialAgentService(
            ChatModel chatModel,
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            ChatMessageRepository chatMessageRepository,
            AgentConfig config,
            FinancialToolAgent financialToolAgent) {
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.chatMessageRepository = chatMessageRepository;
        this.config = config;
        this.financialToolAgent = financialToolAgent;

        this.financialAgent = AiServices.builder(FinancialAgent.class)
                .chatModel(chatModel)
                .tools(financialToolAgent)
                .build();
    }

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

        ChatMessage userMessage = new ChatMessage(finalConversationId, "user", message, null);
        chatMessageRepository.save(userMessage);

        executionHistory.clear();

        long startTime = System.currentTimeMillis();

        // 构建上下文
        String context = buildContext(finalConversationId, message);

        // 查询向量数据库
        if (config.isVectorStoreEnabled()) {
            addExecutionEvent(1, "VectorStore", "STARTED", null, "搜索金融知识库...");
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

        addExecutionEvent(2, "FinancialAgent", "STARTED", null, "AI 正在分析...");

        String enhancedPrompt = buildFinancialPrompt(context, message);
        String response = financialAgent.chat(enhancedPrompt);

        addExecutionEvent(2, "FinancialAgent", "COMPLETED",
                System.currentTimeMillis() - startTime, null);

        simulateStreamOutput(response, onChunk);

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

    private String buildFinancialPrompt(String context, String message) {
        return """
                你是一个专业的金融 AI 助手，具备以下能力：

                1. 投资分析：IRR、NPV、ROI 等投资指标计算
                2. 债券分析：债券定价、久期、凸度、收益率计算
                3. 期权分析：Black-Scholes 定价、Greeks 风险指标
                4. 金融计算：摊销计划、现金流分析、复利计算
                5. 金融知识：基于提供的知识库回答金融问题

                %s

                === 用户问题 ===
                %s

                请提供准确、专业的金融分析。如果需要计算，请使用相应的金融计算工具。
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

    public interface FinancialAgent {
        String chat(String message);
    }
}
