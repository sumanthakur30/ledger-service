package com.shopmanagement.ledgerservice.dto;

import java.time.LocalDate;

/** Auto-post overdue credit interest: Dr Debtors, Cr Interest Income. */
public class CreditInterestVoucherRequest {
    private Long postingId;
    private Long customerId;
    private LocalDate asOfDate;
    private Double amount;
    private String narration;

    public Long getPostingId() { return postingId; }
    public void setPostingId(Long postingId) { this.postingId = postingId; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public LocalDate getAsOfDate() { return asOfDate; }
    public void setAsOfDate(LocalDate asOfDate) { this.asOfDate = asOfDate; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public String getNarration() { return narration; }
    public void setNarration(String narration) { this.narration = narration; }
}
