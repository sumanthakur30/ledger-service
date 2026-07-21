package com.shopmanagement.ledgerservice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.ledgerservice.model.ShopPeriodLock;
import com.shopmanagement.ledgerservice.model.ShopPeriodLockId;

public interface ShopPeriodLockRepository extends JpaRepository<ShopPeriodLock, ShopPeriodLockId> {
    Optional<ShopPeriodLock> findByTenantIdAndShopId(Long tenantId, String shopId);
}
