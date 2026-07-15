package com.shopmanagement.ledgerservice.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "ledger_voucher_lines")
public class LedgerVoucherLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voucher_id")
    @JsonBackReference
    private LedgerVoucher voucher;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(nullable = false)
    private Double debit = 0.0;

    @Column(nullable = false)
    private Double credit = 0.0;

    @Column(name = "line_narration", length = 255)
    private String lineNarration;

    @Column(name = "line_no", nullable = false)
    private int lineNo = 1;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LedgerVoucher getVoucher() { return voucher; }
    public void setVoucher(LedgerVoucher voucher) { this.voucher = voucher; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public Double getDebit() { return debit; }
    public void setDebit(Double debit) { this.debit = debit; }
    public Double getCredit() { return credit; }
    public void setCredit(Double credit) { this.credit = credit; }
    public String getLineNarration() { return lineNarration; }
    public void setLineNarration(String lineNarration) { this.lineNarration = lineNarration; }
    public int getLineNo() { return lineNo; }
    public void setLineNo(int lineNo) { this.lineNo = lineNo; }

    @JsonProperty("voucherId")
    public Long exposeVoucherId() {
        return voucher != null ? voucher.getId() : null;
    }
}
