package com.shopmanagement.ledgerservice.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopmanagement.ledgerservice.model.LedgerVoucher;

public interface LedgerVoucherRepository extends JpaRepository<LedgerVoucher, Long> {
    Optional<LedgerVoucher> findByIdAndTenantIdAndShopId(Long id, Long tenantId, String shopId);

    Optional<LedgerVoucher> findByTenantIdAndShopIdAndSourceTypeAndSourceId(
            Long tenantId, String shopId, String sourceType, Long sourceId);

    @Query("""
            SELECT DISTINCT v FROM LedgerVoucher v
            LEFT JOIN FETCH v.lines
            WHERE v.tenantId = :tenantId AND v.shopId = :shopId
              AND v.voucherDate >= :fromDate AND v.voucherDate <= :toDate
              AND (:voucherType = '' OR v.voucherType = :voucherType)
            ORDER BY v.voucherDate DESC, v.id DESC
            """)
    List<LedgerVoucher> search(
            @Param("tenantId") Long tenantId,
            @Param("shopId") String shopId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("voucherType") String voucherType);

    @Query("""
            SELECT v FROM LedgerVoucher v
            LEFT JOIN FETCH v.lines
            WHERE v.id = :id AND v.tenantId = :tenantId AND v.shopId = :shopId
            """)
    Optional<LedgerVoucher> findDetailedByIdAndTenantIdAndShopId(
            @Param("id") Long id, @Param("tenantId") Long tenantId, @Param("shopId") String shopId);

    @Query("""
            SELECT v FROM LedgerVoucher v
            LEFT JOIN FETCH v.lines
            WHERE v.tenantId = :tenantId AND v.shopId = :shopId
              AND v.sourceType = :sourceType AND v.sourceId = :sourceId
            """)
    Optional<LedgerVoucher> findDetailedBySource(
            @Param("tenantId") Long tenantId,
            @Param("shopId") String shopId,
            @Param("sourceType") String sourceType,
            @Param("sourceId") Long sourceId);
}
