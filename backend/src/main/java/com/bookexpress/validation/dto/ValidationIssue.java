package com.bookexpress.validation.dto;

public class ValidationIssue {
    private String fieldPath;
    private String level; // ERROR/WARN
    private String message;

    public ValidationIssue() {}

    public ValidationIssue(String fieldPath, String level, String message) {
        this.fieldPath = fieldPath;
        this.level = level;
        this.message = message;
    }

    public String getFieldPath() { return fieldPath; }
    public void setFieldPath(String fieldPath) { this.fieldPath = fieldPath; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
