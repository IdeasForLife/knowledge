package com.mark.knowledge.agent.service;

import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * MCP 文件系统服务
 *
 * 提供文件操作工具，类似 Model Context Protocol 的 server-filesystem
 *
 * @author mark
 */
@Component
public class McpFileService {

    private static final Logger log = LoggerFactory.getLogger(McpFileService.class);

    @Value("${agent.mcp-allowed-directory:.}")
    private String allowedDirectory;

    /**
     * 读取文件内容
     */
    @Tool("读取文件内容。参数：文件路径（相对路径）。例如：uploads/document.txt")
    public String readFile(String filePath) {
        log.info("LLM 调用读取文件: {}", filePath);

        try {
            Path resolvedPath = resolvePath(filePath);

            if (!Files.exists(resolvedPath)) {
                return String.format("❌ 文件不存在: %s", filePath);
            }

            if (!Files.isReadable(resolvedPath)) {
                return String.format("❌ 文件不可读: %s", filePath);
            }

            String content = Files.readString(resolvedPath);
            String preview = content.length() > 5000
                ? content.substring(0, 5000) + "\n\n...(文件过长，仅显示前5000字符)"
                : content;

            log.info("文件读取成功: {}, 大小: {} 字符", filePath, content.length());

            return String.format("✓ 文件: %s\n大小: %d 字符\n\n内容:\n%s",
                filePath, content.length(), preview);

        } catch (Exception e) {
            log.error("读取文件失败: {}", filePath, e);
            return String.format("❌ 读取文件失败: %s\n错误: %s", filePath, e.getMessage());
        }
    }

    /**
     * 列出目录内容
     */
    @Tool("列出目录中的文件和文件夹。参数：目录路径（相对路径）。例如：uploads 或留空表示根目录")
    public String listDirectory(String directoryPath) {
        log.info("LLM 调用列出目录: {}", directoryPath);

        try {
            Path resolvedPath = resolvePath(directoryPath.isEmpty() ? "." : directoryPath);

            if (!Files.exists(resolvedPath)) {
                return String.format("❌ 目录不存在: %s", directoryPath);
            }

            if (!Files.isDirectory(resolvedPath)) {
                return String.format("❌ 不是目录: %s", directoryPath);
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("📁 目录: %s\n\n", directoryPath.isEmpty() ? "根目录" : directoryPath));

            List<String> items = new ArrayList<>();

            // 列出文件和目录
            try (var stream = Files.list(resolvedPath)) {
                stream.forEach(item -> {
                    try {
                        Path itemPath = resolvedPath.resolve(item);
                        String type = Files.isDirectory(itemPath) ? "📁" : "📄";
                        String size = Files.isDirectory(itemPath) ? ""
                            : String.format(" (%d 字节)", Files.size(itemPath));
                        items.add(String.format("%s %s%s", type, item, size));
                    } catch (Exception e) {
                        items.add(String.format("❓ %s (无法访问)", item));
                    }
                });
            }

            if (items.isEmpty()) {
                result.append("(目录为空)");
            } else {
                items.forEach(item -> result.append(item).append("\n"));
            }

            log.info("目录列出成功: {}, 项目数: {}", directoryPath, items.size());

            return result.toString();

        } catch (Exception e) {
            log.error("列出目录失败: {}", directoryPath, e);
            return String.format("❌ 列出目录失败: %s\n错误: %s", directoryPath, e.getMessage());
        }
    }

    /**
     * 搜索文件
     */
    @Tool("搜索包含特定内容的文件。参数：搜索关键词、目录路径（可选，留空搜索所有）")
    public String searchFiles(String keyword, String directoryPath) {
        log.info("LLM 调用文件搜索: 关键词={}, 目录={}", keyword, directoryPath);

        try {
            Path searchPath = resolvePath(directoryPath.isEmpty() ? "." : directoryPath);

            if (!Files.exists(searchPath)) {
                return String.format("❌ 目录不存在: %s", directoryPath);
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("🔍 搜索结果: 关键词='%s', 目录=%s\n\n", keyword,
                directoryPath.isEmpty() ? "根目录" : directoryPath));

            List<String> matchedFiles = new ArrayList<>();

            // 递归搜索文件
            Files.walk(searchPath)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        String fileName = path.getFileName().toString();
                        // 搜索文件名
                        if (fileName.toLowerCase().contains(keyword.toLowerCase())) {
                            matchedFiles.add(String.format("📄 %s", fileName));
                        }
                        // 搜索文件内容（仅限小文件）
                        else if (Files.size(path) < 100000) { // 小于100KB
                            String content = Files.readString(path);
                            if (content.toLowerCase().contains(keyword.toLowerCase())) {
                                matchedFiles.add(String.format("📄 %s (内容匹配)", fileName));
                            }
                        }
                    } catch (Exception e) {
                        // 忽略无法读取的文件
                    }
                });

            if (matchedFiles.isEmpty()) {
                result.append("未找到匹配的文件");
            } else {
                matchedFiles.forEach(file -> result.append(file).append("\n"));
                result.append(String.format("\n共找到 %d 个匹配文件", matchedFiles.size()));
            }

            log.info("文件搜索完成: 关键词={}, 结果数={}", keyword, matchedFiles.size());

            return result.toString();

        } catch (Exception e) {
            log.error("文件搜索失败: keyword={}, dir={}", keyword, directoryPath, e);
            return String.format("❌ 文件搜索失败: %s", e.getMessage());
        }
    }

    /**
     * 获取文件信息
     */
    @Tool("获取文件的详细信息。参数：文件路径")
    public String getFileInfo(String filePath) {
        log.info("LLM 调用获取文件信息: {}", filePath);

        try {
            Path resolvedPath = resolvePath(filePath);

            if (!Files.exists(resolvedPath)) {
                return String.format("❌ 文件不存在: %s", filePath);
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("📄 文件信息: %s\n\n", filePath));

            result.append(String.format("- 文件名: %s\n", resolvedPath.getFileName().toString()));
            result.append(String.format("- 绝对路径: %s\n", resolvedPath.toAbsolutePath()));
            result.append(String.format("- 大小: %,d 字节\n", Files.size(resolvedPath)));
            result.append(String.format("- 类型: %s\n", Files.isDirectory(resolvedPath) ? "目录" : "文件"));

            if (!Files.isDirectory(resolvedPath)) {
                String fileName = resolvedPath.getFileName().toString();
                String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1) : "无";
                result.append(String.format("- 扩展名: .%s\n", ext));
            }

            return result.toString();

        } catch (Exception e) {
            log.error("获取文件信息失败: {}", filePath, e);
            return String.format("❌ 获取文件信息失败: %s", e.getMessage());
        }
    }

    /**
     * 解析并限制路径在允许的目录内
     */
    private Path resolvePath(String requestedPath) {
        try {
            Path allowedDir = Paths.get(allowedDirectory).normalize();
            Path requestedDir = allowedDir.resolve(requestedPath).normalize();

            // 确保解析后的路径仍然在允许的目录内
            if (!requestedDir.startsWith(allowedDir)) {
                log.warn("路径被阻止: {} 超出允许的目录 {}", requestedPath, allowedDir);
                throw new SecurityException("路径超出允许的目录范围");
            }

            return requestedDir;
        } catch (Exception e) {
            log.error("路径解析失败: {}", requestedPath, e);
            throw new RuntimeException("无效的文件路径: " + requestedPath, e);
        }
    }

    /**
     * 保存上传的文件（用于处理前端上传）
     */
    public String saveUploadedFile(String fileName, String content, String subDirectory) {
        try {
            // 创建上传目录
            Path uploadDir = Paths.get(allowedDirectory, "uploads", subDirectory);
            Files.createDirectories(uploadDir);

            // 保存文件
            Path filePath = uploadDir.resolve(fileName);
            Files.writeString(filePath, content);

            log.info("文件保存成功: {}", filePath);

            return filePath.toString();

        } catch (Exception e) {
            log.error("保存文件失败: {}", fileName, e);
            throw new RuntimeException("保存文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 读取上传的文件内容（用于与知识库结合）
     */
    public String getUploadedFileContent(String fileName, String subDirectory) {
        try {
            Path uploadDir = Paths.get(allowedDirectory, "uploads", subDirectory);
            Path filePath = uploadDir.resolve(fileName);

            if (!Files.exists(filePath)) {
                throw new RuntimeException("文件不存在: " + fileName);
            }

            return Files.readString(filePath);

        } catch (Exception e) {
            log.error("读取上传文件失败: {}", fileName, e);
            throw new RuntimeException("读取文件失败: " + e.getMessage(), e);
        }
    }
}
