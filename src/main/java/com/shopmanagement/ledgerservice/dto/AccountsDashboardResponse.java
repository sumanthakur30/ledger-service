package com.shopmanagement.ledgerservice.dto;

import java.util.List;

/** Trade accounts dashboard — not polyclinic RevenueLedger / owner command center. */
public record AccountsDashboardResponse(
        int accountCount,
        int postedVoucherCount,
        int draftVoucherCount,
        double periodDebitTotal,
        double periodCreditTotal,
        double debtorsBalance,
        double salesCreditBalance,
        double gstPayableBalance,
        List<TrialBalanceRow> trialBalanceHighlight,
        List<RecentVoucherRow> recentVouchers) {

    public record RecentVoucherRow(
            Long id,
            String voucherNumber,
            String voucherType,
            String status,
            String sourceType,
            Long sourceId,
            double totalDebit,
            String voucherDate) {
    }
}
