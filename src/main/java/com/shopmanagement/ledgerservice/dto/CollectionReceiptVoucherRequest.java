package com.shopmanagement.ledgerservice.dto;

import java.time.LocalDate;

/** Trade collection / AR receipt payload for auto cash/bank voucher (wholesale SI or POS). */
public class CollectionReceiptVoucherRequest {
    private Long paymentId;
    private Long invoiceId;
    private String invoiceNumber;
    /** POS / department bill — narration only; source_id stays paymentId. */
    private Long orderId;
    private String orderNumber;
    private LocalDate paymentDate;
    private Double amount;
    /** CASH → Dr 1000; otherwise Dr 1010 Bank (UPI/NEFT/CHEQUE/CARD). */
    private String paymentMethod;
    private String narration;
    private Long branchId;

    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public Long getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Long invoiceId) { this.invoiceId = invoiceId; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getNarration() { return narration; }
    public void setNarration(String narration) { this.narration = narration; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
}
