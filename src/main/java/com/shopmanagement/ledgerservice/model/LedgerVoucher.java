package com.shopmanagement.ledgerservice.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "ledger_vouchers")
public class LedgerVoucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "shop_id", nullable = false, length = 64)
    private String shopId;

    /** Null = shop-level / unassigned; included in consolidated TB. */
    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "voucher_number", nullable = false, length = 50)
    private String voucherNumber;

    @Column(name = "voucher_date", nullable = false)
    private LocalDate voucherDate;

    @Column(name = "voucher_type", nullable = false, length = 30)
    private String voucherType = "JOURNAL";

    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(length = 500)
    private String narration;

    @Column(name = "source_type", length = 40)
    private String sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "total_debit", nullable = false)
    private Double totalDebit = 0.0;

    @Column(name = "total_credit", nullable = false)
    private Double totalCredit = 0.0;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "voucher", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    @JsonManagedReference
    private List<LedgerVoucherLine> lines = new ArrayList<>();

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

    public void addLine(LedgerVoucherLine line) {
        lines.add(line);
        line.setVoucher(this);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public String getVoucherNumber() { return voucherNumber; }
    public void setVoucherNumber(String voucherNumber) { this.voucherNumber = voucherNumber; }
    public LocalDate getVoucherDate() { return voucherDate; }
    public void setVoucherDate(LocalDate voucherDate) { this.voucherDate = voucherDate; }
    public String getVoucherType() { return voucherType; }
    public void setVoucherType(String voucherType) { this.voucherType = voucherType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNarration() { return narration; }
    public void setNarration(String narration) { this.narration = narration; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public Double getTotalDebit() { return totalDebit; }
    public void setTotalDebit(Double totalDebit) { this.totalDebit = totalDebit; }
    public Double getTotalCredit() { return totalCredit; }
    public void setTotalCredit(Double totalCredit) { this.totalCredit = totalCredit; }
    public LocalDateTime getPostedAt() { return postedAt; }
    public void setPostedAt(LocalDateTime postedAt) { this.postedAt = postedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public List<LedgerVoucherLine> getLines() { return lines; }
    public void setLines(List<LedgerVoucherLine> lines) {
        this.lines = lines != null ? lines : new ArrayList<>();
    }
}
