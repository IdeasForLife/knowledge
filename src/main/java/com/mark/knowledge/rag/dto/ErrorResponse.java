package com.mark.knowledge.rag.dto;

import java.time.LocalDateTime;

/**
 * Standard error response DTO.
 */
public record ErrorResponse(
    String error,
    String message,
    LocalDateTime timestamp
) {
    public ErrorResponse(String error, String message) {
        this(error, message, LocalDateTime.now());
    }
    
    public ErrorResponse(String message) {
        this("Error", message, LocalDateTime.now());
    }
}
