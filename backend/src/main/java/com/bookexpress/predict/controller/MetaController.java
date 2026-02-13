package com.bookexpress.backend.controller;

import com.bookexpress.backend.service.MappingCacheService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Meta endpoints for quick diagnostics.
 */
@RestController
public class MetaController {

    private final MappingCacheService mappingCacheService;

    public MetaController(MappingCacheService mappingCacheService) {
        this.mappingCacheService = mappingCacheService;
    }

    @GetMapping("/api/v1/meta")
    public Map<String, Object> meta() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cacheLoaded", mappingCacheService.isLoaded());
        out.put("categoryCount", mappingCacheService.categoryCount());
        out.put("totalTagLinks", mappingCacheService.totalTagLinks());
        return out;
    }
}
