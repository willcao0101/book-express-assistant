package com.bookexpress.oauth.service;

import com.bookexpress.account.entity.ShopifyAccountEntity;
import com.bookexpress.account.service.AccountService;
import com.bookexpress.common.exception.BusinessException;
import com.bookexpress.oauth.dto.OAuthCallbackRequest;
import com.bookexpress.oauth.dto.OAuthStartRequest;
import com.bookexpress.shopify.util.ShopDomainUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class OAuthService {

    private final AccountService accountService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.shopify.oauth.scopes:read_products,write_products}")
    private String oauthScopes;

    @Value("${app.shopify.oauth.default-redirect-uri:http://localhost:5174/oauth/callback}")
    private String defaultRedirectUri;

    public OAuthService(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Build Shopify OAuth authorization URL.
     * IMPORTANT: must use https://{shop}.myshopify.com/admin/oauth/authorize
     */
    public Map<String, Object> buildStartUrl(OAuthStartRequest req) {
        if (req == null || req.getAccountId() == null) {
            throw new BusinessException("Missing accountId");
        }

        ShopifyAccountEntity account = accountService.getById(req.getAccountId());

        if (!StringUtils.hasText(account.getClientId())) {
            throw new BusinessException("Missing clientId in account");
        }
        if (!StringUtils.hasText(account.getShopDomain())) {
            throw new BusinessException("Missing shopDomain in account");
        }

        // Always normalize domain from DB value
        String normalizedDomain = ShopDomainUtil.normalize(account.getShopDomain());
        if (!ShopDomainUtil.isValidMyShopifyDomain(normalizedDomain)) {
            throw new BusinessException("Invalid shopDomain for OAuth: " + account.getShopDomain()
                    + ". Expected: your-shop.myshopify.com");
        }

        // Optional: persist normalized value back to DB so old dirty data is fixed permanently
        if (!normalizedDomain.equals(account.getShopDomain())) {
            account.setShopDomain(normalizedDomain);
        }

        String redirectUri = StringUtils.hasText(req.getRedirectUri())
                ? req.getRedirectUri().trim()
                : defaultRedirectUri;

        String state = generateState(req.getAccountId(), normalizedDomain);

        String authorizeUrl = UriComponentsBuilder
                .fromHttpUrl("https://" + normalizedDomain + "/admin/oauth/authorize")
                .queryParam("client_id", account.getClientId())
                .queryParam("scope", oauthScopes)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", state)
                .build(true)
                .toUriString();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("authorizeUrl", authorizeUrl);
        data.put("state", state);
        data.put("shopDomain", normalizedDomain);
        data.put("redirectUri", redirectUri);
        return data;
    }

    /**
     * Exchange OAuth code for access token and save into local account.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> handleCallback(OAuthCallbackRequest req) {
        if (req == null || req.getAccountId() == null) {
            throw new BusinessException("Missing accountId");
        }
        if (!StringUtils.hasText(req.getCode())) {
            throw new BusinessException("Missing code");
        }

        ShopifyAccountEntity account = accountService.getById(req.getAccountId());

        if (!StringUtils.hasText(account.getClientId())) {
            throw new BusinessException("Missing clientId in account");
        }
        if (!StringUtils.hasText(account.getClientSecret())) {
            throw new BusinessException("Missing clientSecret in account");
        }
        if (!StringUtils.hasText(account.getShopDomain())) {
            throw new BusinessException("Missing shopDomain in account");
        }

        String normalizedDomain = ShopDomainUtil.normalize(account.getShopDomain());
        if (!ShopDomainUtil.isValidMyShopifyDomain(normalizedDomain)) {
            throw new BusinessException("Invalid shopDomain in account: " + account.getShopDomain());
        }

        String tokenUrl = "https://" + normalizedDomain + "/admin/oauth/access_token";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("client_id", account.getClientId());
        requestBody.put("client_secret", account.getClientSecret());
        requestBody.put("code", req.getCode().trim());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map> response;
        try {
            response = restTemplate.exchange(tokenUrl, HttpMethod.POST, entity, Map.class);
        } catch (Exception ex) {
            throw new BusinessException("Failed to exchange Shopify access token: " + ex.getMessage());
        }

        Map<String, Object> body = response.getBody();
        if (body == null) {
            throw new BusinessException("Empty token response from Shopify");
        }

        Object tokenObj = body.get("access_token");
        if (!(tokenObj instanceof String accessToken) || !StringUtils.hasText(accessToken)) {
            throw new BusinessException("Token response missing access_token: " + body);
        }

        accountService.updateAccessTokenByOAuth(req.getAccountId(), accessToken);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accountId", req.getAccountId());
        result.put("saved", true);
        result.put("shopDomain", normalizedDomain);
        result.put("scope", body.get("scope"));
        return result;
    }

    private String generateState(Long accountId, String shopDomain) {
        String raw = accountId + "|" + shopDomain + "|" + System.currentTimeMillis();
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
