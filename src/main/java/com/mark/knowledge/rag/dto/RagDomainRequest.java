package com.mark.knowledge.rag.dto;

/**
 * 领域文档请求 DTO
 *
 * @author mark
 */
public record RagDomainRequest(
    Domain domain,           // 领域枚举
    String content,          // 文档内容
    String source,           // 来源（可选）
    String title             // 标题（可选）
) {
    // 确保字段不为 null
    public RagDomainRequest {
        if (domain == null) {
            throw new IllegalArgumentException("领域不能为空");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("文档内容不能为空");
        }
        if (source == null || source.trim().isEmpty()) {
            source = "未知来源";
        }
        if (title == null || title.trim().isEmpty()) {
            title = domain.getDisplayName() + "文档";
        }
    }
}
