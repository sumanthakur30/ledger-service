package com.shopmanagement.ledgerservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class OtherIncomeRequest {
    private LocalDate entryDate;
    private String incomeTypeCode;
    private BigDecimal amount;
    private String paymentMode;
    private String cashAccountCode;
    private String payerName;
    private String narration;
    private Long branchId;

    public LocalDate getEntryDate() { return entryDate; }
    public void setEntryDate(LocalDate entryDate) { this.entryDate = entryDate; }
    public String getIncomeTypeCode() { return incomeTypeCode; }
    public void setIncomeTypeCode(String incomeTypeCode) { this.incomeTypeCode = incomeTypeCode; }
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
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
}
