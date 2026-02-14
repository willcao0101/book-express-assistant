package com.bookexpress.oauth.dto;

public class OAuthCallbackRequest {
    private Long accountId;
    private String code;
    private String state; // reserved for future verification

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getCode() {
        return code;
    }

    public OAuthCallbackRequest setCode(String code) {
        this.code = code;
        return this;
    }

    public String getState() {
        return state;
    }

    public OAuthCallbackRequest setState(String state) {
        this.state = state;
        return this;
    }
}
