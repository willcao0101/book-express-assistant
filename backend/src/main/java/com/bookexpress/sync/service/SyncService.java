package com.bookexpress.sync.service;

import com.bookexpress.backend.util.JsonUtil;
import com.bookexpress.common.exception.BusinessException;
import com.bookexpress.shopify.client.ShopifyGraphqlClient;
import com.bookexpress.sync.dto.CommitRequest;
import com.bookexpress.sync.entity.SyncRecordEntity;
import com.bookexpress.sync.repository.SyncRecordRepository;
import com.bookexpress.validation.dto.ValidationResult;
import com.bookexpress.validation.service.ValidationService;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SyncService {

    private final SyncRecordRepository repository;
    private final ValidationService validationService;
    private final ShopifyGraphqlClient shopifyClient;
    private final JsonUtil jsonUtil;

    public SyncService(SyncRecordRepository repository,
                       ValidationService validationService,
                       ShopifyGraphqlClient shopifyClient,
                       JsonUtil jsonUtil) {
        this.repository = repository;
        this.validationService = validationService;
        this.shopifyClient = shopifyClient;
        this.jsonUtil = jsonUtil;
    }

    public SyncRecordEntity commit(CommitRequest req) {
        if (req == null) throw new BusinessException("request is required");
        if (req.getAccountId() == null) throw new BusinessException("accountId is required");
        if (req.getProductId() == null || req.getProductId().isBlank()) throw new BusinessException("productId is required");

        Map<String, Object> payload = req.getUpdatePayload();

        // 1) Validate before commit
        ValidationResult vr = validationService.validate(payload);

        // 2) Always save local record (requirement a)
        SyncRecordEntity record = new SyncRecordEntity();
        record.setAccountId(req.getAccountId());
        record.setProductId(req.getProductId());
        record.setTitle(extractTitle(payload));
        record.setPayloadJson(jsonUtil.toJson(payload));

        if (!vr.isPass()) {
            record.setStatus("FAILED");
            record.setMessage("Validation failed with " + vr.getFailed() + " issue(s).");
            return repository.save(record);
        }

        // 3) Update Shopify + update local record (requirement b)
        try {
            Map<String, Object> resp = shopifyClient.productUpdate(req.getAccountId(), req.getProductId(), payload);
            record.setShopifyResultJson(jsonUtil.toJson(resp));
            record.setStatus("SUCCESS");
            record.setMessage("Updated Shopify and saved local record.");
            return repository.save(record);
        } catch (Exception ex) {
            record.setStatus("FAILED");
            record.setMessage("Shopify update failed: " + ex.getMessage());
            record.setShopifyResultJson(jsonUtil.toJson(Map.of("error", ex.getMessage())));
            return repository.save(record);
        }
    }

    public Page<SyncRecordEntity> list(Long accountId, Long id, String title, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));

        if (id != null) {
            return repository.findById(id, pageable);
        }

        boolean hasTitle = title != null && !title.trim().isEmpty();

        if (accountId != null && hasTitle) {
            return repository.findByAccountIdAndTitleContainingIgnoreCase(accountId, title.trim(), pageable);
        }

        if (accountId != null) {
            return repository.findByAccountId(accountId, pageable);
        }

        if (hasTitle) {
            return repository.findByTitleContainingIgnoreCase(title.trim(), pageable);
        }

        return repository.findAll(pageable);
    }

    @SuppressWarnings("unchecked")
    private String extractTitle(Map<String, Object> payload) {
        if (payload == null) return null;

        Object t = payload.get("title");
        if (t != null && !String.valueOf(t).trim().isEmpty()) {
            return String.valueOf(t).trim();
        }

        Object summaryObj = payload.get("summary");
        if (summaryObj instanceof Map<?, ?> summaryMap) {
            Object st = ((Map<String, Object>) summaryMap).get("title");
            if (st != null && !String.valueOf(st).trim().isEmpty()) {
                return String.valueOf(st).trim();
            }
        }

        return null;
    }
}