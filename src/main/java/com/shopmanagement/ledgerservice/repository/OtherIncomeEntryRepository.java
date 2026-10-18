package com.shopmanagement.ledgerservice.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopmanagement.ledgerservice.model.OtherIncomeEntry;

public interface OtherIncomeEntryRepository extends JpaRepository<OtherIncomeEntry, Long> {
    Optional<OtherIncomeEntry> findByIdAndTenantIdAndShopId(Long id, Long tenantId, String shopId);

    @Query("""
            SELECT e FROM OtherIncomeEntry e
            WHERE e.tenantId = :tenantId AND e.shopId = :shopId
              AND e.entryDate >= :fromDate AND e.entryDate <= :toDate
              AND (:status = '' OR e.status = :status)
            ORDER BY e.entryDate DESC, e.id DESC
            """)
    List<OtherIncomeEntry> search(
            @Param("tenantId") Long tenantId,
            @Param("shopId") String shopId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("status") String status);

    @Query("""
            SELECT COALESCE(SUM(e.amount), 0) FROM OtherIncomeEntry e
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
