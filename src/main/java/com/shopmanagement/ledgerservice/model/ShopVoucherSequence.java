package com.shopmanagement.ledgerservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "shop_voucher_sequences")
@IdClass(ShopVoucherSequenceId.class)
public class ShopVoucherSequence {

    @Id
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Id
    @Column(name = "shop_id", nullable = false, length = 64)
    private String shopId;

    @Id
    @Column(name = "fiscal_year_id", nullable = false)
    private Long fiscalYearId;

    @Id
    @Column(name = "voucher_type", nullable = false, length = 30)
    private String voucherType;

    @Column(name = "next_number", nullable = false)
    private Integer nextNumber = 1;

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }
    public Long getFiscalYearId() { return fiscalYearId; }
    public void setFiscalYearId(Long fiscalYearId) { this.fiscalYearId = fiscalYearId; }
    public String getVoucherType() { return voucherType; }
    public void setVoucherType(String voucherType) { this.voucherType = voucherType; }
    public Integer getNextNumber() { return nextNumber; }
    public void setNextNumber(Integer nextNumber) { this.nextNumber = nextNumber; }
}
