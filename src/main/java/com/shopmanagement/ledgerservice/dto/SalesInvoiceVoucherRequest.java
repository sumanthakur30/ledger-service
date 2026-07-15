package com.shopmanagement.ledgerservice.dto;

import java.time.LocalDate;

/** Trade sales invoice payload for auto journal voucher. */
public class SalesInvoiceVoucherRequest {
    private Long invoiceId;
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private Double subtotalAmount;
    private Double discountAmount;
    private Double taxAmount;
    private Double totalAmount;
    private String narration;

    public Long getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Long invoiceId) { this.invoiceId = invoiceId; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
    public LocalDate getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(LocalDate invoiceDate) { this.invoiceDate = invoiceDate; }
    public Double getSubtotalAmount() { return subtotalAmount; }
    public void setSubtotalAmount(Double subtotalAmount) { this.subtotalAmount = subtotalAmount; }
    public Double getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(Double discountAmount) { this.discountAmount = discountAmount; }
    public Double getTaxAmount() { return taxAmount; }
    public void setTaxAmount(Double taxAmount) { this.taxAmount = taxAmount; }
    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }
    public String getNarration() { return narration; }
    public void setNarration(String narration) { this.narration = narration; }
}
