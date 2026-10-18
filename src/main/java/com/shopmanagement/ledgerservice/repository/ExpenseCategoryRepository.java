package com.shopmanagement.ledgerservice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.ledgerservice.model.ExpenseCategory;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {
    List<ExpenseCategory> findByTenantIdAndShopIdOrderBySortOrderAscCodeAsc(Long tenantId, String shopId);

    Optional<ExpenseCategory> findByIdAndTenantIdAndShopId(Long id, Long tenantId, String shopId);

    Optional<ExpenseCategory> findByTenantIdAndShopIdAndCode(Long tenantId, String shopId, String code);

    boolean existsByTenantIdAndShopIdAndCode(Long tenantId, String shopId, String code);
}
