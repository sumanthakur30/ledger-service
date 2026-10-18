package com.shopmanagement.ledgerservice.dto;

import java.time.LocalDate;

public class ItcReversalVoucherRequest {
    private Long sourceId;
    private LocalDate voucherDate;
    private Long branchId;
    private String documentNumber;
    private String narration;
    private Double inventoryCost;
    private Double cgstAmount;
    private Double sgstAmount;
    private Double igstAmount;
    private Double cessAmount;
    private boolean compensating;

    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public LocalDate getVoucherDate() { return voucherDate; }
    public void setVoucherDate(LocalDate voucherDate) { this.voucherDate = voucherDate; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public String getDocumentNumber() { return documentNumber; }
    public void setDocumentNumber(String documentNumber) { this.documentNumber = documentNumber; }
    public String getNarration() { return narration; }
    public void setNarration(String narration) { this.narration = narration; }
    public Double getInventoryCost() { return inventoryCost; }
    public void setInventoryCost(Double inventoryCost) { this.inventoryCost = inventoryCost; }
    public Double getCgstAmount() { return cgstAmount; }
    public void setCgstAmount(Double cgstAmount) { this.cgstAmount = cgstAmount; }
    public Double getSgstAmount() { return sgstAmount; }
    public void setSgstAmount(Double sgstAmount) { this.sgstAmount = sgstAmount; }
    public Double getIgstAmount() { return igstAmount; }
    public void setIgstAmount(Double igstAmount) { this.igstAmount = igstAmount; }
    public Double getCessAmount() { return cessAmount; }
    public void setCessAmount(Double cessAmount) { this.cessAmount = cessAmount; }
    public boolean isCompensating() { return compensating; }
    public void setCompensating(boolean compensating) { this.compensating = compensating; }
}
