package com.shopmanagement.ledgerservice.dto;

import java.time.LocalDate;

/** Supplier AP payment → Dr Creditors / Cr Cash or Bank. */
public class SupplierPaymentVoucherRequest {
    private Long paymentId;
    private Long supplierId;
    private Long apInvoiceId;
    private Long goodsReceiptId;
    private String documentNumber;
    private LocalDate paymentDate;
    private Double amount;
    private String paymentMethod;
    private String narration;

    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public Long getApInvoiceId() { return apInvoiceId; }
    public void setApInvoiceId(Long apInvoiceId) { this.apInvoiceId = apInvoiceId; }
    public Long getGoodsReceiptId() { return goodsReceiptId; }
    public void setGoodsReceiptId(Long goodsReceiptId) { this.goodsReceiptId = goodsReceiptId; }
    public String getDocumentNumber() { return documentNumber; }
    public void setDocumentNumber(String documentNumber) { this.documentNumber = documentNumber; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getNarration() { return narration; }
    public void setNarration(String narration) { this.narration = narration; }
}
