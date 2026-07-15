package com.shopmanagement.ledgerservice.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CreateVoucherRequest {
    private LocalDate voucherDate;
    private String voucherType = "JOURNAL";
    private String narration;
    private String sourceType;
    private Long sourceId;
    private List<VoucherLineRequest> lines = new ArrayList<>();

    public LocalDate getVoucherDate() { return voucherDate; }
    public void setVoucherDate(LocalDate voucherDate) { this.voucherDate = voucherDate; }
    public String getVoucherType() { return voucherType; }
    public void setVoucherType(String voucherType) { this.voucherType = voucherType; }
    public String getNarration() { return narration; }
    public void setNarration(String narration) { this.narration = narration; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public List<VoucherLineRequest> getLines() { return lines; }
    public void setLines(List<VoucherLineRequest> lines) {
        this.lines = lines != null ? lines : new ArrayList<>();
    }

    public static class VoucherLineRequest {
        private Long accountId;
        private Double debit;
        private Double credit;
        private String lineNarration;

        public Long getAccountId() { return accountId; }
        public void setAccountId(Long accountId) { this.accountId = accountId; }
        public Double getDebit() { return debit; }
        public void setDebit(Double debit) { this.debit = debit; }
        public Double getCredit() { return credit; }
        public void setCredit(Double credit) { this.credit = credit; }
        public String getLineNarration() { return lineNarration; }
        public void setLineNarration(String lineNarration) { this.lineNarration = lineNarration; }
    }
}
