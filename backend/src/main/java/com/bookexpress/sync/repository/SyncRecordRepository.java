package com.bookexpress.sync.repository;

import com.bookexpress.sync.entity.SyncRecordEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncRecordRepository extends JpaRepository<SyncRecordEntity, Long> {
    Page<SyncRecordEntity> findByAccountId(Long accountId, Pageable pageable);
}
