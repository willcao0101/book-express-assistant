package com.bookexpress.account.dto;

public class AccountResponse {
    private Long id;
    private String email;
    private String shopDomain;
    private String clientId;
    private Boolean isDefault;
    private Boolean hasClientSecret;
    private Boolean hasAccessToken;

    public Long getId() {
        return id;
    }

    public AccountResponse setId(Long id) {
        this.id = id;
        return this;
    }

    public String getEmail() {
        return email;
    }

    public AccountResponse setEmail(String email) {
        this.email = email;
        return this;
    }

    public String getShopDomain() {
        return shopDomain;
    }

    public AccountResponse setShopDomain(String shopDomain) {
        this.shopDomain = shopDomain;
        return this;
    }

    public String getClientId() {
        return clientId;
    }

    public AccountResponse setClientId(String clientId) {
        this.clientId = clientId;
        return this;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public AccountResponse setIsDefault(Boolean aDefault) {
        isDefault = aDefault;
        return this;
    }

    public Boolean getHasClientSecret() {
        return hasClientSecret;
    }

    public AccountResponse setHasClientSecret(Boolean hasClientSecret) {
        this.hasClientSecret = hasClientSecret;
        return this;
    }

    public Boolean getHasAccessToken() {
        return hasAccessToken;
    }

    public AccountResponse setHasAccessToken(Boolean hasAccessToken) {
        this.hasAccessToken = hasAccessToken;
        return this;
    }
}
