package com.bookexpress.shopify.controller;

import com.bookexpress.common.web.ApiResponse;
import com.bookexpress.shopify.dto.FetchProductRequest;
import com.bookexpress.shopify.service.ShopifyService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/shopify")
public class ShopifyController {

    private final ShopifyService service;

    public ShopifyController(ShopifyService service) {
        this.service = service;
    }

    /**
     * Fetch one product by product GID.
     * Response includes:
     * - raw: original Shopify response
     * - view: flattened object for frontend rendering
     */
    @PostMapping("/fetch-by-product-id")
    public ApiResponse<Map<String, Object>> fetchByProductId(@RequestBody FetchProductRequest req) {
        return ApiResponse.ok(service.fetchProduct(req.getAccountId(), req.getProductId()));
    }
}
