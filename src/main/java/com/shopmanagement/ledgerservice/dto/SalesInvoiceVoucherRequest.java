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
    private Double cgstAmount;
    private Double sgstAmount;
    private Double igstAmount;
    private Double totalAmount;
    /** Optional COGS (Σ qty × batch purchase price) → Dr 5300 / Cr 1200. */
    private Double cogsAmount;
    private String narration;
    private Long branchId;

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
    public Double getCgstAmount() { return cgstAmount; }
    public void setCgstAmount(Double cgstAmount) { this.cgstAmount = cgstAmount; }
    public Double getSgstAmount() { return sgstAmount; }
    public void setSgstAmount(Double sgstAmount) { this.sgstAmount = sgstAmount; }
    public Double getIgstAmount() { return igstAmount; }
    public void setIgstAmount(Double igstAmount) { this.igstAmount = igstAmount; }
    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }
    public Double getCogsAmount() { return cogsAmount; }
    public void setCogsAmount(Double cogsAmount) { this.cogsAmount = cogsAmount; }
    public String getNarration() { return narration; }
    public void setNarration(String narration) { this.narration = narration; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
}
