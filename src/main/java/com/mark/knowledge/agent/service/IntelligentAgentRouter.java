package com.mark.knowledge.agent.service;

import com.mark.knowledge.agent.config.AgentConfig;
import com.mark.knowledge.agent.dto.AgentExecutionInfo;
import com.mark.knowledge.agent.service.McpFileService;
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
 * 智能体路由服务
 *
 * 根据用户请求类型智能路由到不同的处理方式
 *
 * @author mark
 */
@Service
public class IntelligentAgentRouter {

    private static final Logger log = LoggerFactory.getLogger(IntelligentAgentRouter.class);

    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ChatMessageRepository chatMessageRepository;
    private final AgentConfig config;
    private final RequestClassifier requestClassifier;
    private final ToolAgent toolAgent;
    private final McpFileService mcpFileService;

    private final List<AgentExecutionInfo> executionHistory = new CopyOnWriteArrayList<>();
    private final GeneralAgent generalAgent;

    public IntelligentAgentRouter(
            ChatModel chatModel,
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            ChatMessageRepository chatMessageRepository,
            AgentConfig config,
            RequestClassifier requestClassifier,
            ToolAgent toolAgent,
            McpFileService mcpFileService) {
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.chatMessageRepository = chatMessageRepository;
        this.config = config;
        this.requestClassifier = requestClassifier;
        this.toolAgent = toolAgent;
        this.mcpFileService = mcpFileService;

        this.generalAgent = AiServices.builder(GeneralAgent.class)
                .chatModel(chatModel)
                .tools(mcpFileService)
                .build();
    }

    /**
     * 异步聊天 - 智能路由版本
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
                processChatWithRouting(message, conversationId, onChunk, onComplete);
            } catch (Exception e) {
                log.error("Chat failed", e);
                onError.accept(e);
            }
        });
    }

    private void processChatWithRouting(
            String message,
            String conversationId,
            Consumer<String> onChunk,
            Consumer<String> onComplete) {

        String finalConversationId = conversationId != null ? conversationId : UUID.randomUUID().toString();

        // 保存用户消息
        ChatMessage userMessage = new ChatMessage(finalConversationId, "user", message, null);
        chatMessageRepository.save(userMessage);

        executionHistory.clear();

        // 步骤 1: 分类请求
        addExecutionEvent(1, "RequestClassifier", "STARTED", null, "正在分析请求类型...");
        RequestClassifier.RequestType requestType = requestClassifier.classify(message);
        addExecutionEvent(1, "RequestClassifier", "COMPLETED",
                System.currentTimeMillis(), "请求类型: " + requestType.getDescription());

        log.info("Classified request: {} as {}", message, requestType);

        // 根据类型处理
        String response;
        long startTime = System.currentTimeMillis();

        if (requestType == RequestClassifier.RequestType.CALCULATION) {
            addExecutionEvent(2, "Calculator", "STARTED", null, "执行计算...");
            String result = toolAgent.calculatePublic(message);
            addExecutionEvent(2, "Calculator", "COMPLETED",
                    System.currentTimeMillis() - startTime, result);
            response = result;
        } else if (requestType == RequestClassifier.RequestType.WEATHER) {
            addExecutionEvent(2, "WeatherService", "STARTED", null, "查询天气...");
            String result = toolAgent.getWeatherPublic(extractCity(message));
            addExecutionEvent(2, "WeatherService", "COMPLETED",
                    System.currentTimeMillis() - startTime, result);
            response = result;
        } else if (requestType == RequestClassifier.RequestType.TIME) {
            addExecutionEvent(2, "TimeService", "STARTED", null, "获取时间...");
            String result = toolAgent.getCurrentTimePublic(message);
            addExecutionEvent(2, "TimeService", "COMPLETED",
                    System.currentTimeMillis() - startTime, result);
            response = result;
        } else if (requestType == RequestClassifier.RequestType.KNOWLEDGE) {
            // 知识库查询
            addExecutionEvent(2, "VectorStore", "STARTED", null, "搜索知识库...");
            String context = buildContext(finalConversationId, message);

            String vectorContext = searchVectorStore(message);
            if (!vectorContext.isEmpty()) {
                context += "\n\n=== 相关文档 ===\n" + vectorContext;
                addExecutionEvent(2, "VectorStore", "COMPLETED",
                        System.currentTimeMillis(), "找到相关文档");
            } else {
                addExecutionEvent(2, "VectorStore", "COMPLETED",
                        System.currentTimeMillis(), "未找到相关文档，使用一般对话");
            }

            addExecutionEvent(3, "GeneralChat", "STARTED", null, "生成回复...");
            String enhancedPrompt = buildEnhancedPrompt(context, message);
            response = generalAgent.chat(enhancedPrompt);
            addExecutionEvent(3, "GeneralChat", "COMPLETED",
                    System.currentTimeMillis() - startTime, null);
        } else {
            // GENERAL - 一般对话
            addExecutionEvent(2, "GeneralChat", "STARTED", null, "生成回复...");
            String context = buildContext(finalConversationId, message);
            String enhancedPrompt = buildGeneralPrompt(context, message);
            response = generalAgent.chat(enhancedPrompt);
            addExecutionEvent(2, "GeneralChat", "COMPLETED",
                    System.currentTimeMillis() - startTime, null);
        }

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

    private String buildEnhancedPrompt(String context, String message) {
        return """
                你是一个专业的知识库助手。请基于提供的上下文回答用户的问题。

                %s

                === 用户问题 ===
                %s

                请基于上下文内容准确回答。如果上下文中没有相关信息，请明确告知用户。
                """.formatted(context.isEmpty() ? "" : context, message);
    }

    private String buildGeneralPrompt(String context, String message) {
        return """
                你是一个友好的 AI 助手，可以帮助用户回答问题和完成任务。

                %s

                === 用户问题 ===
                %s

                请提供有帮助的回答。
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

    public interface GeneralAgent {
        String chat(String message);
    }

    // 测试方法
    public String testCalculation(String input) {
        return toolAgent.calculatePublic(input);
    }

    public String testWeather(String input) {
        return toolAgent.getWeatherPublic(extractCity(input));
    }

    public String testTime(String input) {
        return toolAgent.getCurrentTimePublic(input);
    }

    /**
     * 提取城市名称
     */
    private String extractCity(String query) {
        String[] cities = {"北京", "上海", "广州", "深圳", "杭州", "苏州", "南京",
                          "武汉", "成都", "重庆", "西安", "天津", "青岛", "大连",
                          "厦门", "长沙", "郑州", "合肥", "济南", "昆明", "贵阳"};

        for (String city : cities) {
            if (query.contains(city)) {
                return city;
            }
        }

        return "该城市";
    }
}
