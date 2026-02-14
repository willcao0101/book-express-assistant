package com.bookexpress.oauth.dto;

public class OAuthStartRequest {

    private Long accountId;
    private String redirectUri;

    public Long getAccountId() {
        return accountId;
    }

    public OAuthStartRequest setAccountId(Long accountId) {
        this.accountId = accountId;
        return this;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public OAuthStartRequest setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
        return this;
    }
}
