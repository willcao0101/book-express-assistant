package com.bookexpress.account.service;

import com.bookexpress.account.dto.AccountCreateRequest;
import com.bookexpress.account.dto.AccountResponse;
import com.bookexpress.account.dto.AccountUpdateRequest;
import com.bookexpress.account.entity.ShopifyAccountEntity;
import com.bookexpress.account.repository.ShopifyAccountRepository;
import com.bookexpress.common.exception.BusinessException;
import com.bookexpress.shopify.util.ShopDomainUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AccountService {

    private final ShopifyAccountRepository repository;

    public AccountService(ShopifyAccountRepository repository) {
        this.repository = repository;
    }

    /**
     * List all accounts.
     */
    public List<AccountResponse> list() {
        return repository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Create account settings.
     */
    public AccountResponse create(AccountCreateRequest req) {
        validateRequired(req.getEmail(), "email");
        validateRequired(req.getShopDomain(), "shopDomain");
        validateRequired(req.getClientId(), "clientId");

        String normalizedDomain = ShopDomainUtil.normalize(req.getShopDomain());
        if (!ShopDomainUtil.isValidMyShopifyDomain(normalizedDomain)) {
            throw new BusinessException("Invalid shopDomain. Use format: your-shop.myshopify.com");
        }

        ShopifyAccountEntity e = new ShopifyAccountEntity()
                .setEmail(req.getEmail().trim())
                .setShopDomain(normalizedDomain)
                .setClientId(req.getClientId().trim())
                .setIsDefault(Boolean.TRUE.equals(req.getIsDefault()));

        if (StringUtils.hasText(req.getClientSecret())) {
            e.setClientSecret(req.getClientSecret().trim());
        }
        if (StringUtils.hasText(req.getAccessToken())) {
            e.setAccessToken(req.getAccessToken().trim());
        }

        e = repository.save(e);
        return toResponse(e);
    }

    /**
     * Update account settings.
     */
    public AccountResponse update(Long id, AccountUpdateRequest req) {
        ShopifyAccountEntity e = getById(id);

        if (StringUtils.hasText(req.getEmail())) {
            e.setEmail(req.getEmail().trim());
        }

        if (StringUtils.hasText(req.getShopDomain())) {
            String normalizedDomain = ShopDomainUtil.normalize(req.getShopDomain());
            if (!ShopDomainUtil.isValidMyShopifyDomain(normalizedDomain)) {
                throw new BusinessException("Invalid shopDomain. Use format: your-shop.myshopify.com");
            }
            e.setShopDomain(normalizedDomain);
        }

        if (StringUtils.hasText(req.getClientId())) {
            e.setClientId(req.getClientId().trim());
        }

        if (req.getIsDefault() != null) {
            e.setIsDefault(req.getIsDefault());
        }

        // Keep old values when incoming values are blank.
        if (StringUtils.hasText(req.getClientSecret())) {
            e.setClientSecret(req.getClientSecret().trim());
        }
        if (StringUtils.hasText(req.getAccessToken())) {
            e.setAccessToken(req.getAccessToken().trim());
        }

        e = repository.save(e);
        return toResponse(e);
    }

    /**
     * Get account entity by id.
     */
    public ShopifyAccountEntity getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException("Account not found: " + id));
    }

    /**
     * Internal helper for Shopify client to get raw access token.
     */
    public String getAccessTokenPlain(Long accountId) {
        ShopifyAccountEntity e = getById(accountId);
        if (!StringUtils.hasText(e.getAccessToken())) {
            throw new BusinessException("Access token is missing for account: " + accountId);
        }
        return e.getAccessToken().trim();
    }

    /**
     * Save OAuth exchanged token.
     */
    public void updateAccessTokenByOAuth(Long accountId, String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            throw new BusinessException("OAuth token is empty");
        }
        ShopifyAccountEntity e = getById(accountId);
        e.setAccessToken(accessToken.trim());
        repository.save(e);
    }

    private void validateRequired(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException("Missing required field: " + fieldName);
        }
    }

    private AccountResponse toResponse(ShopifyAccountEntity e) {
        return new AccountResponse()
                .setId(e.getId())
                .setEmail(e.getEmail())
                .setShopDomain(e.getShopDomain())
                .setClientId(e.getClientId())
                .setIsDefault(Boolean.TRUE.equals(e.getIsDefault()))
                .setHasClientSecret(StringUtils.hasText(e.getClientSecret()))
                .setHasAccessToken(StringUtils.hasText(e.getAccessToken()));
    }
}
