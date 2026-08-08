package com.shopmanagement.ledgerservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ExpenseEntryRequest {
    private LocalDate entryDate;
    private Long categoryId;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private String currency;
    private String paymentMode;
    private String cashAccountCode;
    private String vendorName;
    private String narration;
    private String attachmentUri;

    public LocalDate getEntryDate() { return entryDate; }
    public void setEntryDate(LocalDate entryDate) { this.entryDate = entryDate; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getPaymentMode() { return paymentMode; }
    public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }
    public String getCashAccountCode() { return cashAccountCode; }
    public void setCashAccountCode(String cashAccountCode) { this.cashAccountCode = cashAccountCode; }
    public String getVendorName() { return vendorName; }
    public void setVendorName(String vendorName) { this.vendorName = vendorName; }
    public String getNarration() { return narration; }
    public void setNarration(String narration) { this.narration = narration; }
    public String getAttachmentUri() { return attachmentUri; }
    public void setAttachmentUri(String attachmentUri) { this.attachmentUri = attachmentUri; }
}
