package com.shopmanagement.ledgerservice.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Bank / cash statement CSV import + match against existing GL recon ticks. */
public final class BankReconDtos {

    private BankReconDtos() {
    }

    public record BatchSummary(
            Long id,
            String accountCode,
            String fileName,
            LocalDate fromDate,
            LocalDate toDate,
            int lineCount,
            int skippedCount,
            LocalDateTime createdAt,
            String createdBy) {
    }

    public record MatchRow(
            String status,
            Integer statementLineNo,
            Long statementLineId,
            Long booksLineId,
            Long voucherId,
            String voucherNumber,
            LocalDate statementDate,
            LocalDate booksDate,
            String statementDescription,
            String booksNarration,
            String statementRef,
            double statementDebit,
            double statementCredit,
            double booksDebit,
            double booksCredit,
            boolean booksReconciled,
            String note) {
    }

    public record MatchResponse(
            Long batchId,
            String accountCode,
            String accountName,
            String fileName,
            LocalDate fromDate,
            LocalDate toDate,
            int statementCount,
            int skippedCount,
            int matched,
            int amountMismatch,
            int dateMismatch,
            int statementOnly,
            int booksOnly,
            List<MatchRow> rows,
            String disclaimer) {
    }
}
