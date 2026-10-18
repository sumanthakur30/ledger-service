package com.shopmanagement.ledgerservice.dto;

import java.time.LocalDate;

/** Trade GRN / goods receipt payload for auto purchase voucher. No GST fields — ITC is AP_ITC. */
public class GoodsReceiptVoucherRequest {
    private Long goodsReceiptId;
    private String grnNumber;
    private LocalDate receiptDate;
    private Long supplierId;
    /** Stock value (prefer landed: goods + capitalized freight). */
    private Double stockAmount;
    /** Creditors credit (goods + freight); defaults to stockAmount when omitted. */
    private Double creditorsAmount;
    private Double freightAmount;
    private String narration;
    private Long branchId;

    public Long getGoodsReceiptId() { return goodsReceiptId; }
    public void setGoodsReceiptId(Long goodsReceiptId) { this.goodsReceiptId = goodsReceiptId; }
    public String getGrnNumber() { return grnNumber; }
    public void setGrnNumber(String grnNumber) { this.grnNumber = grnNumber; }
    public LocalDate getReceiptDate() { return receiptDate; }
    public void setReceiptDate(LocalDate receiptDate) { this.receiptDate = receiptDate; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public Double getStockAmount() { return stockAmount; }
    public void setStockAmount(Double stockAmount) { this.stockAmount = stockAmount; }
    public Double getCreditorsAmount() { return creditorsAmount; }
    public void setCreditorsAmount(Double creditorsAmount) { this.creditorsAmount = creditorsAmount; }
    public Double getFreightAmount() { return freightAmount; }
    public void setFreightAmount(Double freightAmount) { this.freightAmount = freightAmount; }
    public String getNarration() { return narration; }
    public void setNarration(String narration) { this.narration = narration; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
}
