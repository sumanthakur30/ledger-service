package com.shopmanagement.ledgerservice.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopmanagement.ledgerservice.model.ExpenseEntry;

public interface ExpenseEntryRepository extends JpaRepository<ExpenseEntry, Long> {
    Optional<ExpenseEntry> findByIdAndTenantIdAndShopId(Long id, Long tenantId, String shopId);

    @Query("""
            SELECT e FROM ExpenseEntry e
            WHERE e.tenantId = :tenantId AND e.shopId = :shopId
              AND e.entryDate >= :fromDate AND e.entryDate <= :toDate
              AND (:status = '' OR e.status = :status)
              AND (:categoryId IS NULL OR e.categoryId = :categoryId)
            ORDER BY e.entryDate DESC, e.id DESC
            """)
    List<ExpenseEntry> search(
            @Param("tenantId") Long tenantId,
            @Param("shopId") String shopId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("status") String status,
            @Param("categoryId") Long categoryId);

    @Query("""
            SELECT COALESCE(SUM(e.amount), 0) FROM ExpenseEntry e
            WHERE e.tenantId = :tenantId AND e.shopId = :shopId
              AND e.status = 'POSTED'
              AND e.entryDate >= :fromDate AND e.entryDate <= :toDate
            """)
    java.math.BigDecimal sumPostedAmount(
            @Param("tenantId") Long tenantId,
            @Param("shopId") String shopId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);
}
