package com.mark.knowledge.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.mark.knowledge.chat.entity.ChatMessage;
import com.mark.knowledge.chat.repository.ChatMessageRepository;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 聊天服务 - 支持流式响应和SQLite存储
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    @Value("${rag.max-results:5}")
    private int maxResults;

    @Value("${rag.min-score:0.5}")
    private double minScore;

    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ChatMessageRepository chatMessageRepository;
    private final ObjectMapper objectMapper;

    public ChatService(
            ChatModel chatModel,
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            ChatMessageRepository chatMessageRepository,
            ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.chatMessageRepository = chatMessageRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取所有会话ID
     */
    public List<String> getAllConversationIds() {
        return chatMessageRepository.findAllConversationIds();
    }

    /**
     * 获取指定会话的聊天历史
     */
    public List<ChatMessage> getChatHistory(String conversationId) {
        return chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    /**
     * 异步聊天 - 支持流式回调
     */
    @Transactional
    public void chatAsync(String question, String conversationId,
                          Consumer<String> onChunk,
                          Consumer<String> onComplete,
                          Consumer<Exception> onError) {

        CompletableFuture.runAsync(() -> {
            try {
                // 创建新的会话ID
                String finalConversationId = conversationId != null ? conversationId : UUID.randomUUID().toString();

                // 保存用户消息
                ChatMessage userMessage = new ChatMessage(finalConversationId, "user", question, null);
                chatMessageRepository.save(userMessage);

                // 嵌入问题
                var questionEmbedding = embeddingModel.embed(question).content();

                // 搜索相关文档
                EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(questionEmbedding)
                    .maxResults(maxResults)
                    .minScore(minScore)
                    .build();

                EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
                List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

                // 构建上下文
                String context = "";
                List<SourceReference> sources = new ArrayList<>();

                if (!matches.isEmpty()) {
                    context = matches.stream()
                        .map(match -> match.embedded().text())
                        .collect(Collectors.joining("\n\n---\n\n"));

                    sources = matches.stream()
                        .map(match -> {
                            TextSegment segment = match.embedded();
                            String filename = segment.metadata() != null ?
                                segment.metadata().getString("filename") : "unknown";
                            return new SourceReference(filename, segment.text(), match.score());
                        })
                        .collect(Collectors.toList());
                }

                // 获取聊天历史作为上下文
                List<ChatMessage> history = chatMessageRepository
                    .findLastNMessagesByConversationId(finalConversationId, 10);

                StringBuilder historyContext = new StringBuilder();
                for (ChatMessage msg : history) {
                    if ("user".equals(msg.getRole())) {
                        historyContext.append("用户: ").append(msg.getContent()).append("\n");
                    } else {
                        historyContext.append("助手: ").append(msg.getContent()).append("\n");
                    }
                }

                // 构建提示词
                String prompt = buildPrompt(context, historyContext.toString(), question);

                // 生成回答（模拟流式输出）
                String answer = chatModel.chat(prompt);

                // 模拟流式输出 - 分批发送
                simulateStreamOutput(answer, onChunk);

                // 保存助手消息
                String sourcesJson = null;
                if (!sources.isEmpty()) {
                    try {
                        sourcesJson = objectMapper.writeValueAsString(sources);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to serialize sources", e);
                    }
                }

                ChatMessage assistantMessage = new ChatMessage(finalConversationId, "assistant", answer, sourcesJson);
                chatMessageRepository.save(assistantMessage);

                // 回调完成
                onComplete.accept(finalConversationId);

            } catch (Exception e) {
                log.error("Chat failed", e);
                onError.accept(e);
            }
        });
    }

    /**
     * 模拟流式输出 - 将完整文本分批发送
     */
    private void simulateStreamOutput(String text, Consumer<String> onChunk) {
        // 按句子分割文本
        String[] sentences = text.split("(?<=[.!?。！？\\n])");

        for (String sentence : sentences) {
            if (!sentence.trim().isEmpty()) {
                onChunk.accept(sentence);
                try {
                    // 模拟打字延迟
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * 构建提示词
     */
    private String buildPrompt(String context, String history, String question) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是一个有用的AI助手。请基于提供的上下文回答用户的问题。\n\n");

        if (!history.isEmpty()) {
            prompt.append("对话历史:\n").append(history).append("\n");
        }

        if (!context.isEmpty()) {
            prompt.append("相关文档内容:\n").append(context).append("\n\n");
            prompt.append("请基于以上文档内容回答问题。如果文档中没有相关信息，请明确告知。\n\n");
        }

        prompt.append("用户问题: ").append(question).append("\n\n");
        prompt.append("请提供详细、准确的回答:");

        return prompt.toString();
    }

    /**
     * 删除会话
     */
    @Transactional
    public void deleteConversation(String conversationId) {
        chatMessageRepository.deleteByConversationId(conversationId);
        log.info("Deleted conversation: {}", conversationId);
    }

    /**
     * 清空所有聊天记录
     */
    @Transactional
    public void clearAllHistory() {
        chatMessageRepository.deleteAll();
        log.info("Cleared all chat history");
    }

    /**
     * 来源引用
     */
    public static class SourceReference {
        private String filename;
        private String excerpt;
        private double score;

        public SourceReference(String filename, String excerpt, double score) {
            this.filename = filename;
            this.excerpt = excerpt;
            this.score = score;
        }

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }

        public String getExcerpt() {
            return excerpt;
        }

        public void setExcerpt(String excerpt) {
            this.excerpt = excerpt;
        }

        public double getScore() {
            return score;
        }

        public void setScore(double score) {
            this.score = score;
        }
    }
}
