package com.mark.knowledge.rag.service;

import com.mark.knowledge.rag.entity.DomainDocument;
import com.mark.knowledge.rag.repository.DomainDocumentRepository;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 领域文档服务 - 异步处理文档并保存到向量数据库
 *
 * @author mark
 */
@Service
public class DomainDocumentService {

    private static final Logger log = LoggerFactory.getLogger(DomainDocumentService.class);

    @Value("${rag.chunk-size:500}")
    private int chunkSize;

    @Value("${rag.chunk-overlap:50}")
    private int chunkOverlap;

    private final DomainDocumentRepository repository;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    public DomainDocumentService(
            DomainDocumentRepository repository,
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore) {
        this.repository = repository;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    /**
     * 异步处理文档并保存到向量数据库
     */
    @Async("documentProcessingExecutor")
    @Transactional
    public void processDocumentAsync(Long documentId) {
        log.info("开始异步处理文档: ID={}", documentId);

        DomainDocument document = repository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在: " + documentId));

        try {
            // 更新状态为处理中
            document.setStatus("processing");
            repository.save(document);

            // 分割文档内容
            List<TextSegment> segments = splitText(document.getContent(), document.getDomain(), document.getId().toString());

            log.info("文档 {} 分割完成，共 {} 个片段", documentId, segments.size());

            // 生成向量并存储
            int vectorCount = storeSegments(segments);

            // 更新状态为成功
            document.setStatus("success");
            document.setVectorCount(vectorCount);
            document.setCompletedAt(java.time.LocalDateTime.now());
            repository.save(document);

            log.info("文档 {} 处理成功，共保存 {} 个向量", documentId, vectorCount);

        } catch (Exception e) {
            log.error("文档 {} 处理失败", documentId, e);

            // 更新状态为失败
            document.setStatus("failed");
            document.setErrorMessage(e.getMessage());
            document.setCompletedAt(java.time.LocalDateTime.now());
            repository.save(document);
        }
    }

    /**
     * 保存文档记录到数据库
     */
    @Transactional
    public DomainDocument saveDocument(String domain, String title, String source, String content) {
        log.info("保存文档记录: domain={}, title={}", domain, title);

        DomainDocument document = new DomainDocument(domain, title, source, content);
        DomainDocument saved = repository.save(document);

        log.info("文档记录保存成功: ID={}", saved.getId());

        return saved;
    }

    /**
     * 分页查询文档
     */
    public Page<DomainDocument> getDocuments(Pageable pageable) {
        return repository.findAll(pageable);
    }

    /**
     * 根据领域分页查询文档
     */
    public Page<DomainDocument> getDocumentsByDomain(String domain, Pageable pageable) {
        return repository.findByDomain(domain, pageable);
    }

    /**
     * 根据状态分页查询文档
     */
    public Page<DomainDocument> getDocumentsByStatus(String status, Pageable pageable) {
        return repository.findByStatus(status, pageable);
    }

    /**
     * 获取所有领域
     */
    public List<String> getAllDomains() {
        return repository.findAllDistinctDomains();
    }

    /**
     * 分割文本
     */
    private List<TextSegment> splitText(String text, String domain, String documentId) {
        List<TextSegment> segments = new ArrayList<>();

        // 按段落分割
        String[] paragraphs = text.split("\\n\\n+");

        int chunkIndex = 0;
        for (String paragraph : paragraphs) {
            if (paragraph.trim().isEmpty()) {
                continue;
            }

            // 如果段落太长，按句子分割
            if (paragraph.length() > chunkSize) {
                List<TextSegment> subSegments = splitLongParagraph(paragraph, domain, documentId, chunkIndex);
                segments.addAll(subSegments);
                chunkIndex += subSegments.size();
            } else {
                // 创建文本片段
                TextSegment segment = TextSegment.from(paragraph.trim());
                segments.add(segment);
                chunkIndex++;
            }
        }

        return segments;
    }

    /**
     * 分割长段落
     */
    private List<TextSegment> splitLongParagraph(String paragraph, String domain, String documentId, int startIndex) {
        List<TextSegment> segments = new ArrayList<>();

        // 按句子分割
        String[] sentences = paragraph.split("[。！？.!?]");

        StringBuilder currentChunk = new StringBuilder();

        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            // 如果加上这句话会超过块大小，先保存当前块
            if (currentChunk.length() + trimmed.length() > chunkSize && currentChunk.length() > 0) {
                TextSegment segment = TextSegment.from(currentChunk.toString().trim());
                segments.add(segment);
                currentChunk = new StringBuilder();
            }

            currentChunk.append(trimmed).append("。");
        }

        // 保存最后一个块
        if (currentChunk.length() > 0) {
            TextSegment segment = TextSegment.from(currentChunk.toString().trim());
            segments.add(segment);
        }

        return segments;
    }

    /**
     * 存储文本片段到向量数据库
     */
    private int storeSegments(List<TextSegment> segments) {
        log.info("开始存储 {} 个文本片段到向量数据库", segments.size());

        int batchSize = 100;
        int totalStored = 0;

        for (int i = 0; i < segments.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, segments.size());
            List<TextSegment> batch = segments.subList(i, endIndex);

            for (TextSegment segment : batch) {
                // 生成向量
                var embedding = embeddingModel.embed(segment).content();

                // 存储到向量数据库
                embeddingStore.add(embedding, segment);

                totalStored++;
            }

            log.info("已存储 {}/{} 个文本片段", totalStored, segments.size());
        }

        return totalStored;
    }
}
