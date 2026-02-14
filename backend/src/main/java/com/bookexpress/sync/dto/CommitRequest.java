package com.bookexpress.sync.dto;

import java.util.Map;

public class CommitRequest {
    private Long accountId;
    private String productId;
    private Map<String, Object> updatePayload;

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public Map<String, Object> getUpdatePayload() { return updatePayload; }
    public void setUpdatePayload(Map<String, Object> updatePayload) { this.updatePayload = updatePayload; }
}
