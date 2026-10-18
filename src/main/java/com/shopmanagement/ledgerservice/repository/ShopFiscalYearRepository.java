package com.shopmanagement.ledgerservice.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopmanagement.ledgerservice.model.ShopFiscalYear;

public interface ShopFiscalYearRepository extends JpaRepository<ShopFiscalYear, Long> {

    List<ShopFiscalYear> findByTenantIdAndShopIdOrderByStartDateDesc(Long tenantId, String shopId);

    Optional<ShopFiscalYear> findByIdAndTenantIdAndShopId(Long id, Long tenantId, String shopId);

    Optional<ShopFiscalYear> findByTenantIdAndShopIdAndCode(Long tenantId, String shopId, String code);

    @Query("""
            SELECT y FROM ShopFiscalYear y
            WHERE y.tenantId = :tenantId AND y.shopId = :shopId
              AND y.startDate <= :asOf AND y.endDate >= :asOf
            """)
    Optional<ShopFiscalYear> findCovering(
            @Param("tenantId") Long tenantId,
            @Param("shopId") String shopId,
            @Param("asOf") LocalDate asOf);

    @Query("""
            SELECT COUNT(y) FROM ShopFiscalYear y
            WHERE y.tenantId = :tenantId AND y.shopId = :shopId
              AND y.id <> :excludeId
              AND y.startDate <= :endDate AND y.endDate >= :startDate
            """)
    long countOverlapping(
            @Param("tenantId") Long tenantId,
            @Param("shopId") String shopId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludeId") Long excludeId);
}
