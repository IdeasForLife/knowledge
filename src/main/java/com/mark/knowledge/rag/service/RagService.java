package com.mark.knowledge.rag.service;

import com.mark.knowledge.rag.dto.RagRequest;
import com.mark.knowledge.rag.dto.RagResponse;
import com.mark.knowledge.rag.dto.SourceReference;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RagService - Retrieval-Augmented Generation Implementation
 *
 * Service for Retrieval-Augmented Generation (RAG).
 * Combines semantic search with LLM generation using Ollama models.
 *
 * Handles the complete RAG workflow:
 * 1. Embed user question using Ollama embedding model
 * 2. Search for semantically similar document chunks in Qdrant
 * 3. Build context from relevant chunks
 * 4. Generate answer grounded in retrieved context using Ollama chat model
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    @Value("${rag.max-results:5}")
    private int maxResults;

    @Value("${rag.min-score:0.5}")
    private double minScore;

    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    public RagService(
            ChatModel chatModel,
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore) {
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    /**
     * Answer a question using retrieval-augmented generation.
     *
     * @param request RAG request with question
     * @return RAG response with answer and sources
     */
    public RagResponse ask(RagRequest request) {
        log.info("Processing RAG request: '{}'", request.question());

        try {
            // 1. Embed the question
            var questionEmbedding = embeddingModel.embed(request.question()).content();

            // 2. Find relevant document segments using search with minScore
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(questionEmbedding)
                .maxResults(request.maxResults() != null ? request.maxResults() : maxResults)
                .minScore(minScore)
                .build();

            EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
            List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

            log.info("Found {} matches above MIN_SCORE {} for question", matches.size(), minScore);
            matches.forEach(match -> log.info("Match score: {}", String.format("%.4f", match.score())));

            if (matches.isEmpty()) {
                log.warn("No relevant documents found for question: '{}'", request.question());
                return new RagResponse(
                    "I cannot answer this question based on the provided documents. " +
                    "Please try asking something related to the uploaded content.",
                    request.conversationId(),
                    new ArrayList<>()
                );
            }

            // 3. Build context from retrieved segments
            String context = matches.stream()
                .map(match -> match.embedded().text())
                .collect(Collectors.joining("\n\n---\n\n"));

            // 4. Create prompt with context
            String prompt = String.format("""
                You are a helpful assistant. Answer the question based on the following context.
                If the answer cannot be found in the context, say so clearly.

                Context:
                %s

                Question: %s

                Answer:""", context, request.question());

            // 5. Generate answer
            String answer = chatModel.chat(prompt);

            // 6. Build source references
            List<SourceReference> sources = matches.stream()
                .map(match -> {
                    TextSegment segment = match.embedded();
                    String filename = segment.metadata() != null ?
                        segment.metadata().getString("filename") : "unknown";
                    return new SourceReference(
                        filename,
                        segment.text(),
                        match.score()
                    );
                })
                .collect(Collectors.toList());

            log.info("RAG response generated with {} sources", sources.size());

            return new RagResponse(answer, request.conversationId(), sources);

        } catch (Exception e) {
            log.error("RAG processing failed", e);
            throw new RuntimeException("Failed to process question: " + e.getMessage(), e);
        }
    }
}
