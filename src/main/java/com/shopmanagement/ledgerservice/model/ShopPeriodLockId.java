package com.shopmanagement.ledgerservice.model;

import java.io.Serializable;
import java.util.Objects;

public class ShopPeriodLockId implements Serializable {
    private Long tenantId;
    private String shopId;

    public ShopPeriodLockId() {
    }

    public ShopPeriodLockId(Long tenantId, String shopId) {
        this.tenantId = tenantId;
        this.shopId = shopId;
    }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ShopPeriodLockId that)) {
            return false;
        }
        return Objects.equals(tenantId, that.tenantId) && Objects.equals(shopId, that.shopId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, shopId);
    }
}
