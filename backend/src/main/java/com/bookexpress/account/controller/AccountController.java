package com.bookexpress.account.controller;

import com.bookexpress.account.dto.AccountUpsertRequest;
import com.bookexpress.account.entity.ShopifyAccountEntity;
import com.bookexpress.account.service.AccountService;
import com.bookexpress.common.web.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<ShopifyAccountEntity>> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    public ApiResponse<ShopifyAccountEntity> create(@RequestBody AccountUpsertRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<ShopifyAccountEntity> update(@PathVariable Long id, @RequestBody AccountUpsertRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }
}
