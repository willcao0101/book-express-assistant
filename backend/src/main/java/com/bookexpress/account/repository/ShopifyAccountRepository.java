package com.bookexpress.account.repository;

import com.bookexpress.account.entity.ShopifyAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ShopifyAccountRepository extends JpaRepository<ShopifyAccountEntity, Long> {
    List<ShopifyAccountEntity> findByEmailOrderByIdDesc(String email);
}
