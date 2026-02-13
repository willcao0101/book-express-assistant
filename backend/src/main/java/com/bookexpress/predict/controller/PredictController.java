package com.bookexpress.controller;

import com.bookexpress.backend.model.dto.PredictRequest;
import com.bookexpress.backend.model.dto.PredictResponse;
import com.bookexpress.backend.service.PredictService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PredictController {

    private final PredictService predictService;

    public PredictController(PredictService predictService) {
        this.predictService = predictService;
    }

    @PostMapping("/predict")
    public PredictResponse predict(@RequestBody PredictRequest request) {
        return predictService.predict(request);
    }
}
