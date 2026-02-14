package com.bookexpress.validation.dto;

import java.util.Map;

public class ValidateRequest {
    private Map<String, Object> productData;

    public Map<String, Object> getProductData() { return productData; }
    public void setProductData(Map<String, Object> productData) { this.productData = productData; }
}
