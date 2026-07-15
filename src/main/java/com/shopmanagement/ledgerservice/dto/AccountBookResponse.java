package com.shopmanagement.ledgerservice.dto;

import java.time.LocalDate;
import java.util.List;

/** Cash / bank account book for a period. */
public record AccountBookResponse(
        String accountCode,
        String accountName,
        Long accountId,
        LocalDate fromDate,
        LocalDate toDate,
        double openingBalance,
        double periodDebit,
        double periodCredit,
        double closingBalance,
        List<AccountBookLine> lines) {

    public record AccountBookLine(
            Long voucherId,
            String voucherNumber,
            LocalDate voucherDate,
            String voucherType,
            String narration,
            String sourceType,
            Long sourceId,
            double debit,
            double credit,
            double runningBalance) {
    }
}
