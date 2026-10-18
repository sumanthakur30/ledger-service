package com.shopmanagement.ledgerservice.dto;

import java.time.LocalDate;

/**
 * Path-lab CC settlement journal:
 * Dr Collection-centre expense (gross), Cr Creditors (net), Cr TDS Payable (tds).
 */
public class LabCcSettlementVoucherRequest {
    private Long settlementRunId;
    private LocalDate voucherDate;
    private Double grossAmount;
    private Double tdsAmount;
    private Double netAmount;
    private String narration;
    private Long branchId;

    public Long getSettlementRunId() { return settlementRunId; }
    public void setSettlementRunId(Long settlementRunId) { this.settlementRunId = settlementRunId; }
    public LocalDate getVoucherDate() { return voucherDate; }
    public void setVoucherDate(LocalDate voucherDate) { this.voucherDate = voucherDate; }
    public Double getGrossAmount() { return grossAmount; }
    public void setGrossAmount(Double grossAmount) { this.grossAmount = grossAmount; }
    public Double getTdsAmount() { return tdsAmount; }
    public void setTdsAmount(Double tdsAmount) { this.tdsAmount = tdsAmount; }
    public Double getNetAmount() { return netAmount; }
    public void setNetAmount(Double netAmount) { this.netAmount = netAmount; }
    public String getNarration() { return narration; }
    public void setNarration(String narration) { this.narration = narration; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
}
