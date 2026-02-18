package com.mark.knowledge.rag.app;

import com.mark.knowledge.rag.dto.Domain;
import com.mark.knowledge.rag.dto.RagDomainRequest;
import com.mark.knowledge.rag.entity.DomainDocument;
import com.mark.knowledge.rag.service.DomainDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 领域文档控制器 - 异步处理文档
 *
 * @author mark
 */
@RestController
@RequestMapping("/api/domain")
public class DomainController {

    private static final Logger log = LoggerFactory.getLogger(DomainController.class);

    private final DomainDocumentService domainDocumentService;

    public DomainController(DomainDocumentService domainDocumentService) {
        this.domainDocumentService = domainDocumentService;
    }

    /**
     * 接收领域文档并异步处理
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadDomainDocument(@RequestBody RagDomainRequest request) {
        log.info("收到领域文档上传请求: domain={}, title={}", request.domain(), request.title());

        try {
            // 保存到数据库
            DomainDocument document = domainDocumentService.saveDocument(
                request.domain().getDisplayName(),
                request.title(),
                request.source(),
                request.content()
            );

            // 异步处理文档
            domainDocumentService.processDocumentAsync(document.getId());

            return ResponseEntity.ok(new UploadResponse(
                document.getId(),
                "文档已提交，正在后台处理中",
                "pending"
            ));

        } catch (Exception e) {
            log.error("文档上传失败", e);
            return ResponseEntity.status(500).body(new ErrorResponse("上传失败", e.getMessage()));
        }
    }

    /**
     * 分页查询文档列表
     */
    @GetMapping("/documents")
    public ResponseEntity<Page<DomainDocument>> getDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String status) {

        log.info("查询文档列表: page={}, size={}, domain={}, status={}", page, size, domain, status);

        Sort sort = sortDir.equalsIgnoreCase("desc") 
                ? Sort.by(sortBy).descending() 
                : Sort.by(sortBy).ascending();
        
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<DomainDocument> documents;
        
        if (domain != null && status != null) {
            documents = domainDocumentService.getDocumentsByDomain(domain, pageable);
            // TODO: 需要在 Service 中添加按 domain 和 status 查询的方法
        } else if (domain != null) {
            documents = domainDocumentService.getDocumentsByDomain(domain, pageable);
        } else if (status != null) {
            documents = domainDocumentService.getDocumentsByStatus(status, pageable);
        } else {
            documents = domainDocumentService.getDocuments(pageable);
        }

        return ResponseEntity.ok(documents);
    }

    /**
     * 获取所有可用领域枚举
     */
    @GetMapping("/domain-enums")
    public ResponseEntity<List<Map<String, String>>> getDomainEnums() {
        log.info("获取所有领域枚举");
        List<Map<String, String>> domains = java.util.stream.Stream.of(Domain.values())
                .map(d -> java.util.Map.of(
                        "name", d.name(),
                        "displayName", d.getDisplayName(),
                        "code", d.getCode()
                ))
                .toList();
        return ResponseEntity.ok(domains);
    }

    /**
     * 获取所有已使用的领域列表
     */
    @GetMapping("/domains")
    public ResponseEntity<List<String>> getAllDomains() {
        log.info("获取所有领域列表");
        List<String> domains = domainDocumentService.getAllDomains();
        return ResponseEntity.ok(domains);
    }

    /**
     * 获取文档详情
     */
    @GetMapping("/documents/{id}")
    public ResponseEntity<DomainDocument> getDocument(@PathVariable Long id) {
        log.info("获取文档详情: ID={}", id);
        // TODO: 在 Service 中添加 getById 方法
        return ResponseEntity.ok().build();
    }

    /**
     * 上传响应 DTO
     */
    private record UploadResponse(
        Long documentId,
        String message,
        String status
    ) {}

    /**
     * 错误响应 DTO
     */
    private record ErrorResponse(
        String error,
        String message
    ) {}
}
