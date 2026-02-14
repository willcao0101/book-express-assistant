package com.bookexpress.validation.service;

import com.bookexpress.validation.dto.ValidationIssue;
import com.bookexpress.validation.dto.ValidationResult;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ValidationService {

    // Placeholder rules. Replace with DB-driven rules later.
    public ValidationResult validate(Map<String, Object> productData) {
        ValidationResult result = new ValidationResult();
        result.setTotal(2);

        // Example rule 1: title cannot be empty
        Object title = productData.get("title");
        if (title == null || String.valueOf(title).isBlank()) {
            result.getIssues().add(new ValidationIssue("title", "ERROR", "Title must not be empty"));
        }

        // Example rule 2: tags length should be <= 250 chars
        Object tags = productData.get("tags");
        if (tags != null && String.valueOf(tags).length() > 250) {
            result.getIssues().add(new ValidationIssue("tags", "ERROR", "Tags exceed max length"));
        }

        result.setFailed(result.getIssues().size());
        result.setPass(result.getFailed() == 0);
        return result;
    }
}
