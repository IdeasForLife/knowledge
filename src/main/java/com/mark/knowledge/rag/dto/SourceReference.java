package com.mark.knowledge.rag.dto;

/**
 * Represents a source reference in RAG responses.
 */
public record SourceReference(
    String filename,
    String excerpt,
    double relevanceScore
) {
}
