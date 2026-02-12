package com.bookexpress.backend.model.dto;

public class CandidateTag {
    private String tag;
    private double score;

    public CandidateTag() {}

    public CandidateTag(String tag, double score) {
        this.tag = tag;
        this.score = score;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}
