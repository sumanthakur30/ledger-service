package com.shopmanagement.ledgerservice.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "other_income_entry")
public class OtherIncomeEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "shop_id", nullable = false, length = 64)
    private String shopId;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "income_type_code", nullable = false, length = 40)
    private String incomeTypeCode;

    @Column(name = "ledger_account_id", nullable = false)
    private Long ledgerAccountId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_mode", nullable = false, length = 20)
    private String paymentMode;

    @Column(name = "cash_account_code", length = 40)
    private String cashAccountCode;

    @Column(name = "payer_name", length = 200)
    private String payerName;

    @Column(length = 500)
    private String narration;

    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "voucher_id")
    private Long voucherId;

    @Column(name = "source_type", nullable = false, length = 40)
    private String sourceType = "OTHER_INCOME";

    @Column(name = "branch_shop_id", length = 64)
    private String branchShopId;

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }
    public LocalDate getEntryDate() { return entryDate; }
    public void setEntryDate(LocalDate entryDate) { this.entryDate = entryDate; }
    public String getIncomeTypeCode() { return incomeTypeCode; }
    public void setIncomeTypeCode(String incomeTypeCode) { this.incomeTypeCode = incomeTypeCode; }
    public Long getLedgerAccountId() { return ledgerAccountId; }
    public void setLedgerAccountId(Long ledgerAccountId) { this.ledgerAccountId = ledgerAccountId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getPaymentMode() { return paymentMode; }
    public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }
    public String getCashAccountCode() { return cashAccountCode; }
    public void setCashAccountCode(String cashAccountCode) { this.cashAccountCode = cashAccountCode; }
    public String getPayerName() { return payerName; }
    public void setPayerName(String payerName) { this.payerName = payerName; }
    public String getNarration() { return narration; }
    public void setNarration(String narration) { this.narration = narration; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getVoucherId() { return voucherId; }
    public void setVoucherId(Long voucherId) { this.voucherId = voucherId; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getBranchShopId() { return branchShopId; }
    public void setBranchShopId(String branchShopId) { this.branchShopId = branchShopId; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
