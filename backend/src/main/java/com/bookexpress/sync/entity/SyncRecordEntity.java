package com.bookexpress.sync.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sync_record")
public class SyncRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long accountId;
    private String productId;

    @Column(length = 500)
    private String title;

    private String status; // SUCCESS/FAILED

    @Column(length = 2000)
    private String message;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String payloadJson;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String shopifyResultJson;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }

    public String getShopifyResultJson() { return shopifyResultJson; }
    public void setShopifyResultJson(String shopifyResultJson) { this.shopifyResultJson = shopifyResultJson; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}