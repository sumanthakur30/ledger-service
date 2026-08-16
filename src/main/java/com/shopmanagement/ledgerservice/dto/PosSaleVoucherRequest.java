package com.shopmanagement.ledgerservice.dto;

import java.time.LocalDate;

/** Retail POS / clinic department bill payload for auto journal voucher. */
public class PosSaleVoucherRequest {
    private Long orderId;
    private String orderNumber;
    private LocalDate orderDate;
    private Double subtotalAmount;
    private Double discountAmount;
    private Double taxAmount;
    private Double cgstAmount;
    private Double sgstAmount;
    private Double igstAmount;
    private Double totalAmount;
    private Double paidAmount;
    /** CASH → Dr 1000; otherwise Dr 1010 Bank when cash/bank portion &gt; 0. */
    private String paymentMethod;
    /** PAID / PARTIAL / UNPAID. PENDING is rejected. */
    private String paymentStatus;
    /** Optional COGS (WAC or batch purchase price) → Dr 5300 / Cr 1200. */
    private Double cogsAmount;
    private String billType;
    private String narration;
    private Long branchId;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public LocalDate getOrderDate() { return orderDate; }
    public void setOrderDate(LocalDate orderDate) { this.orderDate = orderDate; }
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
    public Double getPaidAmount() { return paidAmount; }
    public void setPaidAmount(Double paidAmount) { this.paidAmount = paidAmount; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
    public Double getCogsAmount() { return cogsAmount; }
    public void setCogsAmount(Double cogsAmount) { this.cogsAmount = cogsAmount; }
    public String getBillType() { return billType; }
    public void setBillType(String billType) { this.billType = billType; }
    public String getNarration() { return narration; }
    public void setNarration(String narration) { this.narration = narration; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
}
