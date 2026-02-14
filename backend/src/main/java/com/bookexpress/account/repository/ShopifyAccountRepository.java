package com.bookexpress.account.repository;

import com.bookexpress.account.entity.ShopifyAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopifyAccountRepository extends JpaRepository<ShopifyAccountEntity, Long> {
}
