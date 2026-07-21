package com.shopmanagement.ledgerservice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopmanagement.ledgerservice.model.LedgerVoucherLine;

public interface LedgerVoucherLineRepository extends JpaRepository<LedgerVoucherLine, Long> {

    @Query("""
            SELECT l FROM LedgerVoucherLine l
            JOIN l.voucher v
            WHERE l.id = :lineId AND v.tenantId = :tenantId AND v.shopId = :shopId
            """)
    Optional<LedgerVoucherLine> findScoped(
            @Param("lineId") Long lineId,
            @Param("tenantId") Long tenantId,
            @Param("shopId") String shopId);
}
