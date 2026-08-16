package com.shopmanagement.ledgerservice.model;

import java.io.Serializable;
import java.util.Objects;

public class ShopVoucherSequenceId implements Serializable {

    private Long tenantId;
    private String shopId;
    private Long fiscalYearId;
    private String voucherType;

    public ShopVoucherSequenceId() {
    }

    public ShopVoucherSequenceId(Long tenantId, String shopId, Long fiscalYearId, String voucherType) {
        this.tenantId = tenantId;
        this.shopId = shopId;
        this.fiscalYearId = fiscalYearId;
        this.voucherType = voucherType;
    }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }
    public Long getFiscalYearId() { return fiscalYearId; }
    public void setFiscalYearId(Long fiscalYearId) { this.fiscalYearId = fiscalYearId; }
    public String getVoucherType() { return voucherType; }
    public void setVoucherType(String voucherType) { this.voucherType = voucherType; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ShopVoucherSequenceId that)) {
            return false;
        }
        return Objects.equals(tenantId, that.tenantId)
                && Objects.equals(shopId, that.shopId)
                && Objects.equals(fiscalYearId, that.fiscalYearId)
                && Objects.equals(voucherType, that.voucherType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, shopId, fiscalYearId, voucherType);
    }
}
