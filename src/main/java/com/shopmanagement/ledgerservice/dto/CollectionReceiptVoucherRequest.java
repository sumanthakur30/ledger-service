package com.shopmanagement.ledgerservice.dto;

import java.time.LocalDate;

/** Trade collection / AR receipt payload for auto cash/bank voucher. */
public class CollectionReceiptVoucherRequest {
    private Long paymentId;
    private Long invoiceId;
    private String invoiceNumber;
    private LocalDate paymentDate;
    private Double amount;
    /** CASH → Dr 1000; otherwise Dr 1010 Bank (UPI/NEFT/CHEQUE/CARD). */
    private String paymentMethod;
    private String narration;

    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public Long getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Long invoiceId) { this.invoiceId = invoiceId; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getNarration() { return narration; }
    public void setNarration(String narration) { this.narration = narration; }
}
