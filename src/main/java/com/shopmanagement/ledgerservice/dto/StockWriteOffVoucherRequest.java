package com.shopmanagement.ledgerservice.dto;

import java.time.LocalDate;

/** Stock dump / breakage / expiry write-off → Dr 5400 / Cr 1200. */
public class StockWriteOffVoucherRequest {
    private Long writeOffId;
    private String writeOffNumber;
    private LocalDate writeOffDate;
    private Double amount;
    private String narration;

    public Long getWriteOffId() { return writeOffId; }
    public void setWriteOffId(Long writeOffId) { this.writeOffId = writeOffId; }
    public String getWriteOffNumber() { return writeOffNumber; }
    public void setWriteOffNumber(String writeOffNumber) { this.writeOffNumber = writeOffNumber; }
    public LocalDate getWriteOffDate() { return writeOffDate; }
    public void setWriteOffDate(LocalDate writeOffDate) { this.writeOffDate = writeOffDate; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public String getNarration() { return narration; }
    public void setNarration(String narration) { this.narration = narration; }
}
