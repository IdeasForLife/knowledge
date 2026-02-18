package com.mark.knowledge.rag.dto;

import java.util.Map;

/**
 * Request DTO for uploading a document.
 */
public record DocumentUploadRequest(
    String content,
    String filename,
    String contentType,
    Map<String, String> metadata
) {
    public DocumentUploadRequest {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Content cannot be null or blank");
        }
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Filename cannot be null or blank");
        }
    }
}
