package com.bookexpress.validation.controller;

import com.bookexpress.common.web.ApiResponse;
import com.bookexpress.validation.dto.ValidateRequest;
import com.bookexpress.validation.dto.ValidationResult;
import com.bookexpress.validation.service.ValidationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/validation")
public class ValidationController {

    private final ValidationService service;

    public ValidationController(ValidationService service) {
        this.service = service;
    }

    @PostMapping("/run")
    public ApiResponse<ValidationResult> run(@RequestBody ValidateRequest req) {
        return ApiResponse.ok(service.validate(req.getProductData()));
    }
}
