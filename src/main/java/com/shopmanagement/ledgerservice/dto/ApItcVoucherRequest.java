package com.shopmanagement.ledgerservice.dto;

import java.time.LocalDate;

/**
 * AP invoice GST → Input ITC voucher. GRN already posted Stock/Creditors ex-tax;
 * this request carries only the document GST split (no stock amount — do not revalue GRN).
 */
public class ApItcVoucherRequest {
    private Long apInvoiceId;
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private Long goodsReceiptId;
    private Double taxAmount;
    private Double cgstAmount;
    private Double sgstAmount;
    private Double igstAmount;
    private String narration;
    private Long branchId;

    public Long getApInvoiceId() { return apInvoiceId; }
    public void setApInvoiceId(Long apInvoiceId) { this.apInvoiceId = apInvoiceId; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
    public LocalDate getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(LocalDate invoiceDate) { this.invoiceDate = invoiceDate; }
    public Long getGoodsReceiptId() { return goodsReceiptId; }
    public void setGoodsReceiptId(Long goodsReceiptId) { this.goodsReceiptId = goodsReceiptId; }
    public Double getTaxAmount() { return taxAmount; }
    public void setTaxAmount(Double taxAmount) { this.taxAmount = taxAmount; }
    public Double getCgstAmount() { return cgstAmount; }
    public void setCgstAmount(Double cgstAmount) { this.cgstAmount = cgstAmount; }
    public Double getSgstAmount() { return sgstAmount; }
    public void setSgstAmount(Double sgstAmount) { this.sgstAmount = sgstAmount; }
    public Double getIgstAmount() { return igstAmount; }
    public void setIgstAmount(Double igstAmount) { this.igstAmount = igstAmount; }
    public String getNarration() { return narration; }
    public void setNarration(String narration) { this.narration = narration; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
}
