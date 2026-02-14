package com.bookexpress.account.controller;

import com.bookexpress.account.dto.AccountCreateRequest;
import com.bookexpress.account.dto.AccountResponse;
import com.bookexpress.account.dto.AccountUpdateRequest;
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
    public ApiResponse<List<AccountResponse>> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    public ApiResponse<AccountResponse> create(@RequestBody AccountCreateRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<AccountResponse> update(@PathVariable Long id, @RequestBody AccountUpdateRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }
}
