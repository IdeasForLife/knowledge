package com.mark.knowledge.rag.dto;

/**
 * Response DTO for document operations.
 */
public record DocumentResponse(
    String documentId,
    String filename,
    String message,
    int segmentCount
) {
    public static DocumentResponse success(String documentId, String filename, int segmentCount) {
        return new DocumentResponse(
            documentId,
            filename,
            "Document processed successfully",
            segmentCount
        );
    }
    
    public static DocumentResponse error(String message) {
        return new DocumentResponse(
            null,
            null,
            message,
            0
        );
    }
}
