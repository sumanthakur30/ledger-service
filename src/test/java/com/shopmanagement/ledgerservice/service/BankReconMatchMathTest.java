package com.shopmanagement.ledgerservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.shopmanagement.ledgerservice.service.BankReconMatchMath.BooksLine;
import com.shopmanagement.ledgerservice.service.BankReconMatchMath.MatchRow;
import com.shopmanagement.ledgerservice.service.BankReconMatchMath.StatementLine;

class BankReconMatchMathTest {

    @Test
    void matchesAmountAndSameDate() {
        List<MatchRow> rows = BankReconMatchMath.match(
                List.of(stmt(1, LocalDate.of(2026, 8, 16), "UPI", "", 0, 500)),
                List.of(book(10, LocalDate.of(2026, 8, 16), "POS cash", "RV-1", 500, 0, false)));
        assertEquals(1, rows.size());
        assertEquals(BankReconMatchMath.MATCHED, rows.get(0).status());
        assertEquals(10L, rows.get(0).booksLineId());
    }

    @Test
    void matchesWithinOneDayAndOneRupee() {
        List<MatchRow> rows = BankReconMatchMath.match(
                List.of(stmt(1, LocalDate.of(2026, 8, 16), "NEFT", "", 0, 999.20)),
                List.of(book(11, LocalDate.of(2026, 8, 17), "Collection", "RV-2", 1000.00, 0, false)));
        assertEquals(BankReconMatchMath.MATCHED, rows.get(0).status());
    }

    @Test
    void doesNotMatchWhenAmountOffByMoreThanOne() {
        List<MatchRow> rows = BankReconMatchMath.match(
                List.of(stmt(1, LocalDate.of(2026, 8, 16), "UPI", "", 0, 500)),
                List.of(book(12, LocalDate.of(2026, 8, 16), "POS", "RV-3", 501.01, 0, false)));
        assertEquals(2, rows.size());
        assertEquals(BankReconMatchMath.STATEMENT_ONLY, rows.get(0).status());
        assertEquals(BankReconMatchMath.BOOKS_ONLY, rows.get(1).status());
    }

    @Test
    void sameRefAmountMismatch() {
        List<MatchRow> rows = BankReconMatchMath.match(
                List.of(stmt(1, LocalDate.of(2026, 8, 16), "CHQ", "CHQ4401", 2000, 0)),
                List.of(book(13, LocalDate.of(2026, 8, 16), "Supplier CHQ4401", "PV-9", 0, 1800, false)));
        assertEquals(1, rows.size());
        assertEquals(BankReconMatchMath.AMOUNT_MISMATCH, rows.get(0).status());
        assertEquals(13L, rows.get(0).booksLineId());
    }

    @Test
    void sameRefDateMismatch() {
        List<MatchRow> rows = BankReconMatchMath.match(
                List.of(stmt(1, LocalDate.of(2026, 8, 1), "CHQ", "CHQ4401", 0, 800)),
                List.of(book(14, LocalDate.of(2026, 8, 10), "Receipt CHQ4401", "RV-4", 800, 0, false)));
        assertEquals(BankReconMatchMath.DATE_MISMATCH, rows.get(0).status());
    }

    @Test
    void paymentDoesNotMatchReceipt() {
        List<MatchRow> rows = BankReconMatchMath.match(
                List.of(stmt(1, LocalDate.of(2026, 8, 16), "out", "", 500, 0)),
                List.of(book(15, LocalDate.of(2026, 8, 16), "in", "RV-5", 500, 0, false)));
        assertEquals(BankReconMatchMath.STATEMENT_ONLY, rows.get(0).status());
        assertEquals(BankReconMatchMath.BOOKS_ONLY, rows.get(1).status());
    }

    @Test
    void alreadyReconciledBooksAreNotBooksOnly() {
        List<MatchRow> rows = BankReconMatchMath.match(
                List.of(stmt(1, LocalDate.of(2026, 8, 16), "new", "", 0, 100)),
                List.of(book(16, LocalDate.of(2026, 8, 10), "old", "RV-6", 50, 0, true)));
        assertEquals(1, rows.size());
        assertEquals(BankReconMatchMath.STATEMENT_ONLY, rows.get(0).status());
    }

    @Test
    void rematchStillFindsAlreadyTickedLine() {
        List<MatchRow> rows = BankReconMatchMath.match(
                List.of(stmt(1, LocalDate.of(2026, 8, 16), "UPI", "", 0, 250)),
                List.of(book(17, LocalDate.of(2026, 8, 16), "POS", "RV-7", 250, 0, true)));
        assertEquals(1, rows.size());
        assertEquals(BankReconMatchMath.MATCHED, rows.get(0).status());
        assertTrue(rows.get(0).booksReconciled());
    }

    @Test
    void oneToOneGreedyDoesNotReuseBooksLine() {
        List<MatchRow> rows = BankReconMatchMath.match(
                List.of(
                        stmt(1, LocalDate.of(2026, 8, 16), "a", "", 0, 100),
                        stmt(2, LocalDate.of(2026, 8, 16), "b", "", 0, 100)),
                List.of(book(18, LocalDate.of(2026, 8, 16), "once", "RV-8", 100, 0, false)));
        long matched = rows.stream().filter(r -> BankReconMatchMath.MATCHED.equals(r.status())).count();
        long statementOnly = rows.stream().filter(r -> BankReconMatchMath.STATEMENT_ONLY.equals(r.status())).count();
        assertEquals(1, matched);
        assertEquals(1, statementOnly);
    }

    private static StatementLine stmt(
            int no, LocalDate date, String desc, String ref, double debit, double credit) {
        return new StatementLine(no, (long) no, date, desc, ref, debit, credit);
    }

    private static BooksLine book(
            long id, LocalDate date, String narr, String voucher, double debit, double credit, boolean reconciled) {
        return new BooksLine(id, id + 100, voucher, date, narr, "POS_SALE", id, debit, credit, reconciled);
    }
}
