package com.shopmanagement.ledgerservice.dto;

import java.time.LocalDate;

/**
 * Purchase return / debit note → reverse Creditors + Input GST (and Stock when goods go back).
 * GST must be the document split — do not invent 50/50.
 */
public class PurchaseDebitNoteVoucherRequest {
    private Long purchaseReturnId;
    private String returnNumber;
    private String debitNoteNumber;
    private LocalDate returnDate;
    private Double stockAmount;
    private Double taxAmount;
    private Double cgstAmount;
    private Double sgstAmount;
    private Double igstAmount;
    /** When false, goods stay off this voucher (existing return already credited Stock). */
    private Boolean creditStock;
    private String narration;
    private Long branchId;

    public Long getPurchaseReturnId() { return purchaseReturnId; }
    public void setPurchaseReturnId(Long purchaseReturnId) { this.purchaseReturnId = purchaseReturnId; }
    public String getReturnNumber() { return returnNumber; }
    public void setReturnNumber(String returnNumber) { this.returnNumber = returnNumber; }
    public String getDebitNoteNumber() { return debitNoteNumber; }
    public void setDebitNoteNumber(String debitNoteNumber) { this.debitNoteNumber = debitNoteNumber; }
    public LocalDate getReturnDate() { return returnDate; }
    public void setReturnDate(LocalDate returnDate) { this.returnDate = returnDate; }
    public Double getStockAmount() { return stockAmount; }
    public void setStockAmount(Double stockAmount) { this.stockAmount = stockAmount; }
    public Double getTaxAmount() { return taxAmount; }
    public void setTaxAmount(Double taxAmount) { this.taxAmount = taxAmount; }
    public Double getCgstAmount() { return cgstAmount; }
    public void setCgstAmount(Double cgstAmount) { this.cgstAmount = cgstAmount; }
    public Double getSgstAmount() { return sgstAmount; }
    public void setSgstAmount(Double sgstAmount) { this.sgstAmount = sgstAmount; }
    public Double getIgstAmount() { return igstAmount; }
    public void setIgstAmount(Double igstAmount) { this.igstAmount = igstAmount; }
    public Boolean getCreditStock() { return creditStock; }
    public void setCreditStock(Boolean creditStock) { this.creditStock = creditStock; }
    public String getNarration() { return narration; }
    public void setNarration(String narration) { this.narration = narration; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
}
