package com.bookexpress.sync.repository;

import com.bookexpress.sync.entity.SyncRecordEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncRecordRepository extends JpaRepository<SyncRecordEntity, Long> {

    Page<SyncRecordEntity> findByAccountId(Long accountId, Pageable pageable);

    // Added: query by record id
    Page<SyncRecordEntity> findById(Long id, Pageable pageable);

    // Added: query by title contains (ignore case)
    Page<SyncRecordEntity> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    // Added: query by accountId + title
    Page<SyncRecordEntity> findByAccountIdAndTitleContainingIgnoreCase(Long accountId, String title, Pageable pageable);
}
