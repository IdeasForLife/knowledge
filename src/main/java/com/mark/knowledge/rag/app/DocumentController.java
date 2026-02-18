package com.mark.knowledge.rag.app;


import com.mark.knowledge.rag.dto.DocumentResponse;
import com.mark.knowledge.rag.dto.ErrorResponse;
import com.mark.knowledge.rag.service.DocumentService;
import com.mark.knowledge.rag.service.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 文档上传和管理控制器
 *
 * @author mark
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    private final DocumentService documentService;
    private final EmbeddingService embeddingService;

    public DocumentController(
            DocumentService documentService,
            EmbeddingService embeddingService) {
        this.documentService = documentService;
        this.embeddingService = embeddingService;
    }

    /**
     * 上传并处理文档
     *
     * @param file 文档文件（PDF 或 TXT 格式）
     * @return 处理结果
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadDocument(@RequestParam("file") MultipartFile file) {
        log.info("收到文档上传请求: {}", file.getOriginalFilename());

        try {
            // 验证文件
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(new ErrorResponse("无效文件", "文件为空"));
            }

            String filename = file.getOriginalFilename();
            if (filename == null) {
                return ResponseEntity.badRequest()
                    .body(new ErrorResponse("无效文件", "文件名缺失"));
            }

            // 处理文档
            try (InputStream inputStream = file.getInputStream()) {
                DocumentService.ProcessedDocument processed = documentService.processDocument(
                    inputStream,
                    filename
                );

                // 存储向量嵌入
                int embeddingCount = embeddingService.storeSegments(processed.segments());

                log.info("文档处理成功: {} ({} 个片段)",
                    filename, embeddingCount);

                return ResponseEntity.ok(new DocumentResponse(
                    processed.documentId(),
                    filename,
                    "文档处理成功",
                    embeddingCount
                ));
            }

        } catch (Exception e) {
            log.error("文档处理失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("处理失败", e.getMessage()));
        }
    }

    /**
     * 健康检查接口
     *
     * @return 健康状态
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("文档服务运行正常");
    }
}
