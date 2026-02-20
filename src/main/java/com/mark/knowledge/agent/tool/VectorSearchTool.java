package com.mark.knowledge.agent.tool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 向量检索工具
 *
 * 将向量数据库检索封装为 Agent 可调用的工具
 * Agent 可以自主决定是否需要检索知识库
 *
 * @author mark
 */
@Component
public class VectorSearchTool {

    private static final Logger log = LoggerFactory.getLogger(VectorSearchTool.class);

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    // 用于记录工具调用历史
    private Consumer<ToolCallRecord> recordCallback;

    public VectorSearchTool(
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    public void setRecordCallback(Consumer<ToolCallRecord> callback) {
        this.recordCallback = callback;
    }

    /**
     * 在知识库中搜索相关文档
     *
     * @param query 搜索查询
     * @param maxResults 最大结果数（可选，默认5）
     * @return 检索到的相关文档
     */
    @Tool("""
            在知识库中搜索相关文档。

            使用场景：
            - 用户询问文档、知识库中的信息时
            - 需要查找特定主题的文档时
            - 需要引用文档内容来回答问题时

            参数说明：
            - query: 搜索关键词或问题
            - maxResults: 返回的最大结果数（默认5）
            """)
    public String searchKnowledge(String query, Integer maxResults) {

        long startTime = System.currentTimeMillis();
        log.info("🔍 Agent调用向量检索工具: query={}", query);

        int maxRes = maxResults != null ? maxResults : 5;

        try {
            // 生成查询向量
            var questionEmbedding = embeddingModel.embed(query).content();

            // 搜索相关文档
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(questionEmbedding)
                    .maxResults(maxRes)
                    .minScore(0.5)
                    .build();

            EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
            var matches = searchResult.matches();

            if (matches.isEmpty()) {
                log.info("❌ 未找到相关文档");

                // 记录调用
                recordCall("searchKnowledge", query, "未找到相关文档",
                          System.currentTimeMillis() - startTime, true);

                return "未在知识库中找到相关文档。";
            }

            // 构建结果
            String context = matches.stream()
                    .map(match -> {
                        TextSegment segment = match.embedded();
                        String filename = segment.metadata() != null ?
                                segment.metadata().getString("filename") : "unknown";
                        double score = match.score();
                        return String.format("[来源: %s, 相似度: %.2f]\n%s", filename, score, segment.text());
                    })
                    .collect(Collectors.joining("\n\n---\n\n"));

            long duration = System.currentTimeMillis() - startTime;
            log.info("✅ 找到 {} 条相关文档 (耗时: {}ms)", matches.size(), duration);

            // 记录调用
            recordCall("searchKnowledge", query, String.format("找到%d条文档", matches.size()),
                      duration, true);

            return context;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("向量检索失败", e);

            // 记录失败
            recordCall("searchKnowledge", query, null, duration, false);

            return "向量检索失败: " + e.getMessage();
        }
    }

    private void recordCall(String toolName, String input, String result,
                           long duration, boolean success) {
        if (recordCallback != null) {
            recordCallback.accept(new ToolCallRecord(
                toolName, input, result, duration, success
            ));
        }
    }

    /**
     * 工具调用记录
     */
    public record ToolCallRecord(
        String toolName,
        String input,
        String result,
        long duration,
        boolean success
    ) {
        public String getStatus() {
            return success ? "SUCCESS" : "FAILED";
        }
    }
}
