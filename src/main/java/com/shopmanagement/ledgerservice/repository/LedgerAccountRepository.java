package com.shopmanagement.ledgerservice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.ledgerservice.model.LedgerAccount;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, Long> {
    List<LedgerAccount> findByTenantIdAndShopIdOrderByCodeAsc(Long tenantId, String shopId);

    Optional<LedgerAccount> findByIdAndTenantIdAndShopId(Long id, Long tenantId, String shopId);

    Optional<LedgerAccount> findByTenantIdAndShopIdAndCode(Long tenantId, String shopId, String code);

    boolean existsByTenantIdAndShopIdAndCode(Long tenantId, String shopId, String code);

    long countByTenantIdAndShopId(Long tenantId, String shopId);
}
