package com.mark.knowledge.rag.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 嵌入服务 - 使用Ollama生成和管理嵌入向量
 *
 * 服务职责：
 * - 为文本块生成嵌入向量
 * - 将嵌入向量存储到Qdrant向量数据库
 * - 批量处理和进度跟踪
 * - 性能监控和日志记录
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    /**
     * 构造函数
     *
     * @param embeddingModel Ollama嵌入模型
     * @param embeddingStore Qdrant向量存储
     */
    public EmbeddingService(
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    /**
     * 为文本块生成并存储嵌入向量
     *
     * 处理流程：
     * 1. 使用Ollama模型生成嵌入向量
     * 2. 批量存储到Qdrant（每批100个）
     * 3. 记录进度和性能指标
     *
     * @param segments 文本块列表
     * @return 成功创建的嵌入向量数量
     */
    public int storeSegments(List<TextSegment> segments) {
        long startTime = System.currentTimeMillis();

        log.info("==========================================");
        log.info("嵌入向量存储开始");
        log.info("  待处理文本块总数: {}", segments.size());
        log.info("==========================================");

        try {
            // 步骤1：生成嵌入向量
            log.info("🔢 [步骤1/2] 为 {} 个文本块生成嵌入向量...", segments.size());
            long embedStart = System.currentTimeMillis();

            Response<List<Embedding>> response = embeddingModel.embedAll(segments);
            List<Embedding> embeddings = response.content();

            long embedTime = System.currentTimeMillis() - embedStart;
            log.info("✓ 嵌入向量生成成功，耗时: {} ms", embedTime);
            log.info("  向量维度: {}", embeddings.get(0).dimension());

            // 步骤2：存储嵌入向量
            log.info("💾 [步骤2/2] 存储嵌入向量到Qdrant...");
            long storeStart = System.currentTimeMillis();

            int batchSize = 100;
            int totalStored = 0;

            // 批量存储并记录进度
            for (int i = 0; i < segments.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, segments.size());
                int batchCount = endIndex - i;

                List<Embedding> embeddingBatch = embeddings.subList(i, endIndex);
                List<TextSegment> segmentBatch = segments.subList(i, endIndex);

                embeddingStore.addAll(embeddingBatch, segmentBatch);
                totalStored += batchCount;

                // 记录进度
                double progress = (endIndex * 100.0) / segments.size();
                log.info("  进度: {}/{} 文本块 ({}%) 已存储",
                         endIndex, segments.size(), String.format("%.1f", progress));
            }

            long storeTime = System.currentTimeMillis() - storeStart;
            log.info("✓ 所有嵌入向量存储成功，耗时: {} ms", storeTime);

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("==========================================");
            log.info("嵌入向量存储完成");
            log.info("  已存储嵌入向量总数: {}", totalStored);
            log.info("  嵌入生成耗时: {} ms", embedTime);
            log.info("  向量存储耗时: {} ms", storeTime);
            log.info("  总耗时: {} ms", totalTime);
            log.info("  平均每个嵌入耗时: {} ms",
                     String.format("%.2f", (double)totalTime / totalStored));
            log.info("==========================================");

            return totalStored;

        } catch (Exception e) {
            long totalTime = System.currentTimeMillis() - startTime;
            log.error("==========================================");
            log.error("嵌入向量存储失败");
            log.error("  待处理文本块数: {}", segments.size());
            log.error("  已用时间: {} ms", totalTime);
            log.error("  错误: {}", e.getMessage(), e);
            log.error("==========================================");
            throw new RuntimeException("嵌入向量存储失败: " + e.getMessage(), e);
        }
    }
}
