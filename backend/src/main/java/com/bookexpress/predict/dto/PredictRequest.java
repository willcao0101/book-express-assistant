package com.bookexpress.backend.model.dto;

/**
 * Request DTO for prediction endpoint.
 * Using record so service can call req.title(), req.author(), req.topK(), req.useAI().
 */
public record PredictRequest(
        String title,
        String author,
        Integer topK,
        Boolean useAI
) {}
