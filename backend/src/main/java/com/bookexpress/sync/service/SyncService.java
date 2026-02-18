package com.bookexpress.sync.service;

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

    public SyncService(SyncRecordRepository repository,
                       ValidationService validationService,
                       ShopifyGraphqlClient shopifyClient) {
        this.repository = repository;
        this.validationService = validationService;
        this.shopifyClient = shopifyClient;
    }

    public SyncRecordEntity commit(CommitRequest req) {
        if (req == null) throw new BusinessException("request is required");
        if (req.getAccountId() == null) throw new BusinessException("accountId is required");
        if (req.getProductId() == null || req.getProductId().isBlank()) throw new BusinessException("productId is required");

        // Always validate on server side before commit
        ValidationResult vr = validationService.validate(req.getUpdatePayload());

        SyncRecordEntity record = new SyncRecordEntity();
        record.setAccountId(req.getAccountId());
        record.setProductId(req.getProductId());

        // Persist title for records list display (already used by Records page)
        record.setTitle(extractTitle(req.getUpdatePayload()));

        if (!vr.isPass()) {
            record.setStatus("FAILED");
            record.setMessage("Validation failed with " + vr.getFailed() + " issue(s).");
            return repository.save(record);
        }

        // Commit to Shopify + save local record
        try {
            shopifyClient.productUpdate(req.getAccountId(), req.getProductId(), req.getUpdatePayload());
            record.setStatus("SUCCESS");
            record.setMessage("Updated Shopify and saved local record.");
            return repository.save(record);
        } catch (Exception ex) {
            record.setStatus("FAILED");
            record.setMessage("Shopify update failed: " + ex.getMessage());
            return repository.save(record);
        }
    }

    public Page<SyncRecordEntity> list(Long accountId, Long id, String title, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));

        // id has highest priority
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

        // Common shape in your frontend update payload: { title: "..." }
        Object t = payload.get("title");
        if (t != null && !String.valueOf(t).trim().isEmpty()) {
            return String.valueOf(t).trim();
        }

        // Fallback: summary.title if exists
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
