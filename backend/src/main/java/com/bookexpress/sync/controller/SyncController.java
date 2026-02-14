package com.bookexpress.sync.controller;

import com.bookexpress.common.web.ApiResponse;
import com.bookexpress.sync.dto.CommitRequest;
import com.bookexpress.sync.entity.SyncRecordEntity;
import com.bookexpress.sync.service.SyncService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class SyncController {

    private final SyncService service;

    public SyncController(SyncService service) {
        this.service = service;
    }

    @PostMapping("/commit")
    public ApiResponse<SyncRecordEntity> commit(@RequestBody CommitRequest req) {
        return ApiResponse.ok(service.commit(req));
    }

    @GetMapping("/records")
    public ApiResponse<Page<SyncRecordEntity>> records(
            @RequestParam(required = false) Long accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(service.list(accountId, page, size));
    }
}
