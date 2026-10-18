package com.shopmanagement.ledgerservice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopmanagement.ledgerservice.model.ShopVoucherSequence;
import com.shopmanagement.ledgerservice.model.ShopVoucherSequenceId;

import jakarta.persistence.LockModeType;

public interface ShopVoucherSequenceRepository extends JpaRepository<ShopVoucherSequence, ShopVoucherSequenceId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s FROM ShopVoucherSequence s
            WHERE s.tenantId = :tenantId AND s.shopId = :shopId
              AND s.fiscalYearId = :fiscalYearId AND s.voucherType = :voucherType
            """)
    Optional<ShopVoucherSequence> findForUpdate(
            @Param("tenantId") Long tenantId,
            @Param("shopId") String shopId,
            @Param("fiscalYearId") Long fiscalYearId,
            @Param("voucherType") String voucherType);
}
