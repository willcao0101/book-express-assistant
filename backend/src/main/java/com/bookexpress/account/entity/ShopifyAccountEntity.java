package com.bookexpress.account.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "shopify_account")
public class ShopifyAccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(name = "shop_domain", nullable = false)
    private String shopDomain;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "client_secret")
    private String clientSecret;

    @Column(name = "access_token")
    private String accessToken;

    @Column(name = "is_default")
    private Boolean isDefault = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public ShopifyAccountEntity setId(Long id) {
        this.id = id;
        return this;
    }

    public String getEmail() {
        return email;
    }

    public ShopifyAccountEntity setEmail(String email) {
        this.email = email;
        return this;
    }

    public String getShopDomain() {
        return shopDomain;
    }

    public ShopifyAccountEntity setShopDomain(String shopDomain) {
        this.shopDomain = shopDomain;
        return this;
    }

    public String getClientId() {
        return clientId;
    }

    public ShopifyAccountEntity setClientId(String clientId) {
        this.clientId = clientId;
        return this;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public ShopifyAccountEntity setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
        return this;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public ShopifyAccountEntity setAccessToken(String accessToken) {
        this.accessToken = accessToken;
        return this;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public ShopifyAccountEntity setIsDefault(Boolean aDefault) {
        isDefault = aDefault;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public ShopifyAccountEntity setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public ShopifyAccountEntity setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }
}
