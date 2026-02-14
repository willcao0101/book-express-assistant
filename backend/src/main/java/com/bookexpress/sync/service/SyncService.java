package com.bookexpress.sync.service;

import com.bookexpress.sync.dto.CommitRequest;
import com.bookexpress.sync.entity.SyncRecordEntity;
import com.bookexpress.sync.repository.SyncRecordRepository;
import com.bookexpress.validation.dto.ValidationResult;
import com.bookexpress.validation.service.ValidationService;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

@Service
public class SyncService {

    private final SyncRecordRepository repository;
    private final ValidationService validationService;

    public SyncService(SyncRecordRepository repository, ValidationService validationService) {
        this.repository = repository;
        this.validationService = validationService;
    }

    public SyncRecordEntity commit(CommitRequest req) {
        // Always validate on server side before commit
        ValidationResult vr = validationService.validate(req.getUpdatePayload());

        SyncRecordEntity record = new SyncRecordEntity();
        record.setAccountId(req.getAccountId());
        record.setProductId(req.getProductId());

        if (!vr.isPass()) {
            record.setStatus("FAILED");
            record.setMessage("Validation failed with " + vr.getFailed() + " issue(s).");
            return repository.save(record);
        }

        // TODO: Call Shopify mutation here.
        // For now, we store a success record as a skeleton.
        record.setStatus("SUCCESS");
        record.setMessage("Committed to Shopify (skeleton).");
        return repository.save(record);
    }

    public Page<SyncRecordEntity> list(Long accountId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        if (accountId == null) return repository.findAll(pageable);
        return repository.findByAccountId(accountId, pageable);
    }
}
