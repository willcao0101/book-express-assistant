package com.bookexpress.shopify.dto;

public class FetchProductRequest {
    private Long accountId;
    private String productId; // gid://shopify/Product/xxx

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
}
