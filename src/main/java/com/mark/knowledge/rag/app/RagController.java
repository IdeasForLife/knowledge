package com.mark.knowledge.rag.app;

import com.mark.knowledge.rag.dto.ErrorResponse;
import com.mark.knowledge.rag.dto.RagRequest;
import com.mark.knowledge.rag.dto.RagResponse;
import com.mark.knowledge.rag.service.RagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * RAG（检索增强生成）查询控制器
 *
 * @author mark
 */
@RestController
@RequestMapping("/api/rag")
public class RagController {

    private static final Logger log = LoggerFactory.getLogger(RagController.class);

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    /**
     * 使用 RAG 回答问题
     *
     * @param request RAG 请求（包含问题）
     * @return 答案及来源信息
     */
    @PostMapping("/ask")
    public ResponseEntity<?> ask(@RequestBody RagRequest request) {
        log.info("收到 RAG 问题: {}", request.question());

        try {
            // 验证请求
            if (request.question() == null || request.question().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(new ErrorResponse("无效请求", "问题不能为空"));
            }

            // 处理 RAG 请求
            RagResponse response = ragService.ask(request);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("RAG 请求失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("请求失败", e.getMessage()));
        }
    }

    /**
     * 健康检查接口
     *
     * @return 健康状态
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("RAG 服务运行正常");
    }
}
