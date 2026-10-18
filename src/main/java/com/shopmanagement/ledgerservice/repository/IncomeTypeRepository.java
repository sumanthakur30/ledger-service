package com.shopmanagement.ledgerservice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.ledgerservice.model.IncomeType;

public interface IncomeTypeRepository extends JpaRepository<IncomeType, Long> {
    List<IncomeType> findByTenantIdAndShopIdOrderBySortOrderAscCodeAsc(Long tenantId, String shopId);

    Optional<IncomeType> findByIdAndTenantIdAndShopId(Long id, Long tenantId, String shopId);

    Optional<IncomeType> findByTenantIdAndShopIdAndCode(Long tenantId, String shopId, String code);

    boolean existsByTenantIdAndShopIdAndCode(Long tenantId, String shopId, String code);
}
