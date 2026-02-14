package com.bookexpress.account.service;

import com.bookexpress.account.dto.AccountUpsertRequest;
import com.bookexpress.account.entity.ShopifyAccountEntity;
import com.bookexpress.account.repository.ShopifyAccountRepository;
import com.bookexpress.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AccountService {

    private final ShopifyAccountRepository repository;
    private final CryptoService cryptoService;

    public AccountService(ShopifyAccountRepository repository, CryptoService cryptoService) {
        this.repository = repository;
        this.cryptoService = cryptoService;
    }

    public List<ShopifyAccountEntity> list() {
        return repository.findAll();
    }

    public ShopifyAccountEntity create(AccountUpsertRequest req) {
        ShopifyAccountEntity e = new ShopifyAccountEntity();
        e.setEmail(req.getEmail());
        e.setShopDomain(req.getShopDomain());
        e.setClientId(req.getClientId());
        e.setClientSecretEnc(cryptoService.encrypt(req.getClientSecret()));
        e.setAccessTokenEnc(cryptoService.encrypt(req.getAccessToken()));
        e.setIsDefault(Boolean.TRUE.equals(req.getIsDefault()));
        return repository.save(e);
    }

    public ShopifyAccountEntity update(Long id, AccountUpsertRequest req) {
        ShopifyAccountEntity e = repository.findById(id)
                .orElseThrow(() -> new BusinessException("Account not found: " + id));
        e.setEmail(req.getEmail());
        e.setShopDomain(req.getShopDomain());
        e.setClientId(req.getClientId());
        if (req.getClientSecret() != null) {
            e.setClientSecretEnc(cryptoService.encrypt(req.getClientSecret()));
        }
        if (req.getAccessToken() != null) {
            e.setAccessTokenEnc(cryptoService.encrypt(req.getAccessToken()));
        }
        e.setIsDefault(Boolean.TRUE.equals(req.getIsDefault()));
        return repository.save(e);
    }

    public ShopifyAccountEntity getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException("Account not found: " + id));
    }

    public String getAccessTokenPlain(Long id) {
        ShopifyAccountEntity e = getById(id);
        return cryptoService.decrypt(e.getAccessTokenEnc());
    }
}
