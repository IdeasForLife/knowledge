package com.mark.knowledge.rag.service;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 文档服务 - 文档处理和分块
 *
 * 服务职责：
 * - 处理文档输入（解析和分块）
 * - 支持多种格式（PDF、TXT）
 * - 将文档切分为最优大小的文本块，用于RAG嵌入和检索
 *
 * 核心概念：
 * - 文档分块策略（递归分块）
 * - 分块大小和重叠配置
 * - 多格式文档解析
 * - 元数据保留以便溯源
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    @Value("${rag.chunk-size:500}")
    private int chunkSize;

    @Value("${rag.chunk-overlap:50}")
    private int chunkOverlap;

    /**
     * 处理输入流中的文档
     *
     * @param inputStream 文档输入流
     * @param filename 文件名（含扩展名）
     * @return 处理后的文档（包含文本块）
     */
    public ProcessedDocument processDocument(InputStream inputStream, String filename) {
        long startTime = System.currentTimeMillis();

        log.info("==========================================");
        log.info("文档处理开始");
        log.info("  文件名: {}", filename);
        log.info("  分块大小: {}", chunkSize);
        log.info("  重叠大小: {}", chunkOverlap);
        log.info("==========================================");

        String documentId = UUID.randomUUID().toString();

        try {
            // 步骤1：解析文档
            log.info("📖 [步骤1/3] 解析文档: {}", filename);
            long parseStart = System.currentTimeMillis();

            String content;
            if (filename.toLowerCase().endsWith(".pdf")) {
                content = parsePdf(inputStream);
                log.info("✓ PDF解析成功 ({} 字符)", content.length());
            } else {
                content = parseText(inputStream);
                log.info("✓ 文本解析成功 ({} 字符)", content.length());
            }

            long parseTime = System.currentTimeMillis() - parseStart;
            log.info("  解析完成，耗时: {} ms", parseTime);

            // 验证内容
            if (content == null || content.isBlank()) {
                throw new IllegalArgumentException("文档内容为空");
            }

            // 步骤2：切分文本块
            log.info("✂️  [步骤2/3] 切分文档为文本块...");
            long splitStart = System.currentTimeMillis();

            List<TextSegment> segments = splitText(content, filename, documentId);

            long splitTime = System.currentTimeMillis() - splitStart;
            log.info("✓ 文档已切分为 {} 个文本块，耗时: {} ms", segments.size(), splitTime);
            log.info("  平均文本块大小: {} 字符",
                     content.length() / Math.max(1, segments.size()));

            // 步骤3：创建结果
            log.info("📦 [步骤3/3] 创建处理后的文档结果");

            ProcessedDocument result = new ProcessedDocument(documentId, filename, segments);

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("==========================================");
            log.info("文档处理完成");
            log.info("  文档ID: {}", documentId);
            log.info("  文本块总数: {}", segments.size());
            log.info("  总耗时: {} ms", totalTime);
            log.info("==========================================");

            return result;

        } catch (Exception e) {
            long totalTime = System.currentTimeMillis() - startTime;
            log.error("==========================================");
            log.error("文档处理失败");
            log.error("  文件名: {}", filename);
            log.error("  已用时间: {} ms", totalTime);
            log.error("  错误: {}", e.getMessage(), e);
            log.error("==========================================");
            throw new RuntimeException("文档处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将文本切分为带重叠的文本块
     *
     * @param text 待切分的文本
     * @param filename 文件名（用于元数据）
     * @param documentId 文档ID（用于元数据）
     * @return 文本块列表
     */
    private List<TextSegment> splitText(String text, String filename, String documentId) {
        log.debug("开始文本切分过程...");
        log.debug("  文本总长度: {} 字符", text.length());

        List<TextSegment> segments = new ArrayList<>();

        // 优先按段落切分，避免在句子中间断开
        String[] paragraphs = text.split("\\n\\n+");
        log.debug("  发现 {} 个段落", paragraphs.length);

        StringBuilder currentChunk = new StringBuilder();
        int currentLength = 0;
        int chunkCount = 0;

        for (int i = 0; i < paragraphs.length; i++) {
            String paragraph = paragraphs[i].trim();
            if (paragraph.isEmpty()) {
                continue;
            }

            int paragraphLength = paragraph.length();

            // 如果段落长度超过分块大小，按句子切分
            if (paragraphLength > chunkSize) {
                // 如果当前块有内容，先保存
                if (currentChunk.length() > 0) {
                    segments.add(createSegment(currentChunk.toString(), filename, documentId, chunkCount++));
                    currentChunk = new StringBuilder();
                    currentLength = 0;
                }

                // 按句子切分长段落
                String[] sentences = paragraph.split("(?<=[.!?。！？])\\s+");
                log.debug("  切分长段落 ({} 字符) 为 {} 个句子",
                         paragraphLength, sentences.length);

                for (String sentence : sentences) {
                    if (currentLength + sentence.length() > chunkSize && currentChunk.length() > 0) {
                        segments.add(createSegment(currentChunk.toString(), filename, documentId, chunkCount++));
                        currentChunk = new StringBuilder();
                        currentLength = 0;
                    }
                    currentChunk.append(sentence).append(" ");
                    currentLength += sentence.length() + 1;
                }
            } else {
                // 检查添加此段落是否超过分块大小
                if (currentLength + paragraphLength > chunkSize && currentChunk.length() > 0) {
                    segments.add(createSegment(currentChunk.toString(), filename, documentId, chunkCount++));

                    // 从上一个块添加重叠内容
                    String overlapText = getOverlapText(currentChunk.toString());
                    currentChunk = new StringBuilder(overlapText);
                    currentLength = overlapText.length();
                    log.debug("  文本块 {} 已创建，带重叠: {} 字符",
                             chunkCount - 1, overlapText.length());
                }

                currentChunk.append(paragraph).append("\n\n");
                currentLength += paragraphLength + 2;
            }
        }

        // 添加最后一个块
        if (currentChunk.length() > 0) {
            segments.add(createSegment(currentChunk.toString().trim(), filename, documentId, chunkCount));
        }

        log.debug("文本切分完成: 创建了 {} 个文本块", segments.size());
        return segments;
    }

    /**
     * 创建带元数据的文本块
     *
     * @param text 文本内容
     * @param filename 文件名
     * @param documentId 文档ID
     * @param index 文本块索引
     * @return 文本块对象
     */
    private TextSegment createSegment(String text, String filename, String documentId, int index) {
        Metadata metadata = new Metadata();
        metadata.put("filename", filename);
        metadata.put("documentId", documentId);
        metadata.put("chunkIndex", String.valueOf(index));
        metadata.put("chunkSize", String.valueOf(text.length()));
        return TextSegment.from(text, metadata);
    }

    /**
     * 从文本块末尾获取重叠文本
     *
     * @param text 文本内容
     * @return 重叠的文本
     */
    private String getOverlapText(String text) {
        if (chunkOverlap <= 0 || text.length() <= chunkOverlap) {
            return "";
        }

        // 尝试找到合适的断点（句子边界）
        String overlap = text.substring(text.length() - chunkOverlap);
        int lastSentenceEnd = Math.max(
            Math.max(overlap.lastIndexOf(". "), overlap.lastIndexOf("。")),
            Math.max(overlap.lastIndexOf("! "), overlap.lastIndexOf("！"))
        );

        if (lastSentenceEnd > 0 && lastSentenceEnd < overlap.length() - 1) {
            return overlap.substring(lastSentenceEnd + 1).trim();
        }

        return overlap;
    }

    /**
     * 使用PDFBox 3.x解析PDF文档
     *
     * @param inputStream PDF输入流
     * @return 提取的文本内容
     * @throws IOException 如果PDF解析失败
     */
    private String parsePdf(InputStream inputStream) throws IOException {
        log.debug("  正在读取PDF字节流...");

        // PDFBox 3.x 需要字节数组
        byte[] bytes = inputStream.readAllBytes();
        log.debug("  PDF大小: {} 字节", bytes.length);

        log.debug("  正在加载PDF文档...");
        try (PDDocument document = Loader.loadPDF(bytes)) {
            int pageCount = document.getNumberOfPages();
            log.debug("  PDF页数: {}", pageCount);

            log.debug("  正在从PDF提取文本...");
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);

            // 清理提取的文本
            String cleaned = text
                    .replaceAll("\\r\\n", "\n")  // 规范化换行符
                    .replaceAll("\\s+$", "")      // 移除行尾空白
                    .trim();

            log.debug("  文本提取完成: {} 字符", cleaned.length());
            return cleaned;
        } catch (Exception e) {
            log.error("PDF文档解析失败", e);
            throw new IOException("PDF解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析文本文档
     *
     * @param inputStream 文本输入流
     * @return 文本内容
     */
    private String parseText(InputStream inputStream) {
        log.debug("  正在读取文本文件...");

        try {
            String content = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n"));

            log.debug("  文本文件读取成功: {} 字符", content.length());
            return content.trim();
        } catch (Exception e) {
            log.error("文本文档解析失败", e);
            throw new RuntimeException("文本解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 处理后的文档结果
     *
     * @param documentId 唯一文档标识符
     * @param filename 原始文件名
     * @param segments 带元数据的文本块列表
     */
    public record ProcessedDocument(
        String documentId,
        String filename,
        List<TextSegment> segments
    ) {
    }
}
