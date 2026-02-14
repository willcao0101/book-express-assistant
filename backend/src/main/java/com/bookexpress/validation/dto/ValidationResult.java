package com.bookexpress.validation.dto;

import java.util.ArrayList;
import java.util.List;

public class ValidationResult {
    private boolean pass;
    private int total;
    private int failed;
    private List<ValidationIssue> issues = new ArrayList<>();

    public boolean isPass() { return pass; }
    public void setPass(boolean pass) { this.pass = pass; }
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    public int getFailed() { return failed; }
    public void setFailed(int failed) { this.failed = failed; }
    public List<ValidationIssue> getIssues() { return issues; }
    public void setIssues(List<ValidationIssue> issues) { this.issues = issues; }
}
