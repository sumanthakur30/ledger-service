package com.shopmanagement.ledgerservice.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "bank_statement_lines")
public class BankStatementLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "shop_id", nullable = false, length = 64)
    private String shopId;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "txn_date")
    private LocalDate txnDate;

    @Column(name = "value_date")
    private LocalDate valueDate;

    @Column(length = 500)
    private String description;

    @Column(length = 80)
    private String reference;

    @Column(nullable = false)
    private Double debit = 0.0;

    @Column(nullable = false)
    private Double credit = 0.0;

    private Double balance;

    @Column(name = "match_status", length = 30)
    private String matchStatus;

    @Column(name = "matched_line_id")
    private Long matchedLineId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }
    public int getLineNo() { return lineNo; }
    public void setLineNo(int lineNo) { this.lineNo = lineNo; }
    public LocalDate getTxnDate() { return txnDate; }
    public void setTxnDate(LocalDate txnDate) { this.txnDate = txnDate; }
    public LocalDate getValueDate() { return valueDate; }
    public void setValueDate(LocalDate valueDate) { this.valueDate = valueDate; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public Double getDebit() { return debit; }
    public void setDebit(Double debit) { this.debit = debit; }
    public Double getCredit() { return credit; }
    public void setCredit(Double credit) { this.credit = credit; }
    public Double getBalance() { return balance; }
    public void setBalance(Double balance) { this.balance = balance; }
    public String getMatchStatus() { return matchStatus; }
    public void setMatchStatus(String matchStatus) { this.matchStatus = matchStatus; }
    public Long getMatchedLineId() { return matchedLineId; }
    public void setMatchedLineId(Long matchedLineId) { this.matchedLineId = matchedLineId; }
}
