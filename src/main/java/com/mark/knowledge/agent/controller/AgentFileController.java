package com.mark.knowledge.agent.controller;

import com.mark.knowledge.agent.service.McpFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent 文件上传 Controller
 *
 * 处理 agent-chat.html 的文件上传，并与知识库结合
 *
 * @author mark
 */
@RestController
@RequestMapping("/api/agent-files")
@CrossOrigin(origins = "*")
public class AgentFileController {

    private static final Logger log = LoggerFactory.getLogger(AgentFileController.class);

    @Autowired
    private McpFileService mcpFileService;

    /**
     * 上传文件
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "conversationId", required = false) String conversationId) {

        log.info("接收到文件上传请求: 文件名={}, 对话ID={}", file.getOriginalFilename(), conversationId);

        try {
            // 读取文件内容
            String content = new String(file.getBytes());

            // 保存文件
            String subDirectory = conversationId != null ? conversationId : "general";
            String filePath = mcpFileService.saveUploadedFile(file.getOriginalFilename(), content, subDirectory);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "文件上传成功");
            response.put("fileName", file.getOriginalFilename());
            response.put("filePath", filePath);
            response.put("fileSize", file.getSize());
            response.put("conversationId", subDirectory);

            log.info("文件上传成功: {}", file.getOriginalFilename());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("文件上传失败: {}", file.getOriginalFilename(), e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "文件上传失败: " + e.getMessage());

            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 获取已上传的文件列表
     */
    @GetMapping("/files/{conversationId}")
    public ResponseEntity<Map<String, Object>> getUploadedFiles(@PathVariable String conversationId) {
        try {
            String result = mcpFileService.listDirectory("uploads/" + conversationId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("files", result);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("获取文件列表失败: conversationId={}", conversationId, e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 读取上传的文件内容
     */
    @GetMapping("/files/{conversationId}/{fileName}")
    public ResponseEntity<Map<String, Object>> getUploadedFileContent(
            @PathVariable String conversationId,
            @PathVariable String fileName) {

        try {
            String content = mcpFileService.getUploadedFileContent(fileName, conversationId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("fileName", fileName);
            response.put("content", content);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("读取文件失败: fileName={}, conversationId={}", fileName, conversationId, e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.status(404).body(response);
        }
    }
}
