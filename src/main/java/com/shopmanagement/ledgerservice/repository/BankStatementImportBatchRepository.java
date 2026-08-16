package com.shopmanagement.ledgerservice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.ledgerservice.model.BankStatementImportBatch;

public interface BankStatementImportBatchRepository extends JpaRepository<BankStatementImportBatch, Long> {

    Optional<BankStatementImportBatch> findByIdAndTenantIdAndShopId(Long id, Long tenantId, String shopId);

    List<BankStatementImportBatch> findByTenantIdAndShopIdAndAccountCodeOrderByCreatedAtDescIdDesc(
            Long tenantId, String shopId, String accountCode);

    List<BankStatementImportBatch> findByTenantIdAndShopIdOrderByCreatedAtDescIdDesc(Long tenantId, String shopId);
}
