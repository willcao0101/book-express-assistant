package com.bookexpress.backend.model.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO for prediction endpoint.
 */
public class PredictResponse {
    private String strategy; // EXACT / RULE / LLM
    private String category;
    private List<CandidateTag> tags = new ArrayList<>();
    private double confidence;
    private String reason;
    private boolean allowed;

    // Optional observability fields
    private String selectedBy;
    private boolean llmCalled;
    private boolean llmEffective;

    public PredictResponse() {}

    public PredictResponse(String strategy,
                           String category,
                           List<CandidateTag> tags,
                           double confidence,
                           String reason,
                           boolean allowed) {
        this.strategy = strategy;
        this.category = category;
        this.tags = tags;
        this.confidence = confidence;
        this.reason = reason;
        this.allowed = allowed;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public List<CandidateTag> getTags() {
        return tags;
    }

    public void setTags(List<CandidateTag> tags) {
        this.tags = tags;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    public String getSelectedBy() {
        return selectedBy;
    }

    public void setSelectedBy(String selectedBy) {
        this.selectedBy = selectedBy;
    }

    public boolean isLlmCalled() {
        return llmCalled;
    }

    public void setLlmCalled(boolean llmCalled) {
        this.llmCalled = llmCalled;
    }

    public boolean isLlmEffective() {
        return llmEffective;
    }

    public void setLlmEffective(boolean llmEffective) {
        this.llmEffective = llmEffective;
    }
}
