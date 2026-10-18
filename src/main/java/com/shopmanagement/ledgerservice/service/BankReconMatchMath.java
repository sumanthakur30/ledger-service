package com.shopmanagement.ledgerservice.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Matches imported statement lines to GL cash/bank lines. Does not tick {@code reconciled}.
 */
public final class BankReconMatchMath {

    public static final double AMOUNT_TOLERANCE = 1.0;
    public static final int DATE_TOLERANCE_DAYS = 1;

    public static final String MATCHED = "MATCHED";
    public static final String AMOUNT_MISMATCH = "AMOUNT_MISMATCH";
    public static final String DATE_MISMATCH = "DATE_MISMATCH";
    public static final String STATEMENT_ONLY = "STATEMENT_ONLY";
    public static final String BOOKS_ONLY = "BOOKS_ONLY";

    private BankReconMatchMath() {
    }

    public record BooksLine(
            Long lineId,
            Long voucherId,
            String voucherNumber,
            LocalDate date,
            String narration,
            String sourceType,
            Long sourceId,
            double debit,
            double credit,
            boolean reconciled) {
        public double signedIn() {
            return round2(debit - credit);
        }
    }

    public record StatementLine(
            int lineNo,
            Long statementLineId,
            LocalDate date,
            String description,
            String reference,
            double debit,
            double credit) {
        public double signedIn() {
            return round2(credit - debit);
        }
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

    public static List<MatchRow> match(List<StatementLine> statements, List<BooksLine> books) {
        List<StatementLine> stmts = statements != null ? statements : List.of();
        List<BooksLine> allBooks = books != null ? books : List.of();
        Set<Long> usedBooks = new HashSet<>();
        List<MatchRow> rows = new ArrayList<>();

        for (StatementLine stmt : stmts) {
            if (stmt == null) {
                continue;
            }
            MatchRow refHit = matchByReference(stmt, allBooks, usedBooks);
            if (refHit != null) {
                rows.add(refHit);
                if (refHit.booksLineId() != null) {
                    usedBooks.add(refHit.booksLineId());
                }
                continue;
            }
            BooksLine greedy = bestAmountDate(stmt, allBooks, usedBooks, false);
            if (greedy == null) {
                greedy = bestAmountDate(stmt, allBooks, usedBooks, true);
            }
            if (greedy != null) {
                usedBooks.add(greedy.lineId());
                rows.add(row(MATCHED, stmt, greedy, "Amount and date within ₹1 / ±1 day"));
            } else {
                rows.add(row(STATEMENT_ONLY, stmt, null, "In statement, no unreconciled (or matching) books line"));
            }
        }

        List<BooksLine> leftover = new ArrayList<>();
        for (BooksLine book : allBooks) {
            if (book == null || book.lineId() == null || usedBooks.contains(book.lineId())) {
                continue;
            }
            if (book.reconciled()) {
                continue;
            }
            leftover.add(book);
        }
        leftover.sort(Comparator
                .comparing(BooksLine::date, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(BooksLine::lineId, Comparator.nullsLast(Comparator.naturalOrder())));
        for (BooksLine book : leftover) {
            rows.add(new MatchRow(
                    BOOKS_ONLY,
                    null,
                    null,
                    book.lineId(),
                    book.voucherId(),
                    book.voucherNumber(),
                    null,
                    book.date(),
                    null,
                    book.narration(),
                    null,
                    0,
                    0,
                    book.debit(),
                    book.credit(),
                    book.reconciled(),
                    "In books, not on this statement"));
        }
        return List.copyOf(rows);
    }

    private static MatchRow matchByReference(StatementLine stmt, List<BooksLine> books, Set<Long> used) {
        String ref = normalizeRef(stmt.reference());
        if (ref.length() < 3) {
            return null;
        }
        List<BooksLine> hits = new ArrayList<>();
        for (BooksLine book : books) {
            if (book == null || book.lineId() == null || used.contains(book.lineId())) {
                continue;
            }
            if (refMatches(ref, book)) {
                hits.add(book);
            }
        }
        if (hits.isEmpty()) {
            return null;
        }
        hits.sort(refRank(stmt));
        BooksLine best = hits.get(0);
        boolean amt = amountMatch(stmt.signedIn(), best.signedIn());
        boolean date = dateMatch(stmt.date(), best.date());
        if (amt && date) {
            return row(MATCHED, stmt, best, "Same ref; amount and date within tolerance");
        }
        if (amt) {
            return row(DATE_MISMATCH, stmt, best, "Same ref and amount; date outside ±1 day");
        }
        return row(AMOUNT_MISMATCH, stmt, best, "Same ref; amount differs by more than ₹1");
    }

    private static Comparator<BooksLine> refRank(StatementLine stmt) {
        return Comparator
                .comparing((BooksLine b) -> b.reconciled())
                .thenComparing((BooksLine b) -> !amountMatch(stmt.signedIn(), b.signedIn()))
                .thenComparing((BooksLine b) -> !dateMatch(stmt.date(), b.date()))
                .thenComparing(b -> dateDistance(stmt.date(), b.date()))
                .thenComparing(b -> Math.abs(stmt.signedIn() - b.signedIn()));
    }

    private static BooksLine bestAmountDate(
            StatementLine stmt, List<BooksLine> books, Set<Long> used, boolean includeReconciled) {
        BooksLine best = null;
        long bestDist = Long.MAX_VALUE;
        double bestAmt = Double.MAX_VALUE;
        for (BooksLine book : books) {
            if (book == null || book.lineId() == null || used.contains(book.lineId())) {
                continue;
            }
            if (book.reconciled() && !includeReconciled) {
                continue;
            }
            if (!amountMatch(stmt.signedIn(), book.signedIn()) || !dateMatch(stmt.date(), book.date())) {
                continue;
            }
            long dist = dateDistance(stmt.date(), book.date());
            double amt = Math.abs(stmt.signedIn() - book.signedIn());
            if (best == null || dist < bestDist || (dist == bestDist && amt < bestAmt)
                    || (dist == bestDist && amt == bestAmt && !book.reconciled() && best.reconciled())) {
                best = book;
                bestDist = dist;
                bestAmt = amt;
            }
        }
        return best;
    }

    public static boolean amountMatch(double signedA, double signedB) {
        return Math.abs(round2(signedA) - round2(signedB)) <= AMOUNT_TOLERANCE + 0.0001;
    }

    public static boolean dateMatch(LocalDate a, LocalDate b) {
        if (a == null || b == null) {
            return false;
        }
        return dateDistance(a, b) <= DATE_TOLERANCE_DAYS;
    }

    public static long dateDistance(LocalDate a, LocalDate b) {
        if (a == null || b == null) {
            return Long.MAX_VALUE;
        }
        return Math.abs(ChronoUnit.DAYS.between(a, b));
    }

    public static String normalizeRef(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    static boolean refMatches(String normalizedRef, BooksLine book) {
        if (normalizedRef == null || normalizedRef.length() < 3) {
            return false;
        }
        if (normalizedRef.equals(normalizeRef(book.voucherNumber()))) {
            return true;
        }
        if (book.sourceId() != null && normalizedRef.equals(String.valueOf(book.sourceId()))) {
            return true;
        }
        String narr = normalizeRef(book.narration());
        if (narr.contains(normalizedRef)) {
            return true;
        }
        return normalizedRef.length() >= 4 && normalizedRef.contains(normalizeRef(book.voucherNumber()))
                && normalizeRef(book.voucherNumber()).length() >= 4;
    }

    private static MatchRow row(String status, StatementLine stmt, BooksLine book, String note) {
        return new MatchRow(
                status,
                stmt.lineNo(),
                stmt.statementLineId(),
                book != null ? book.lineId() : null,
                book != null ? book.voucherId() : null,
                book != null ? book.voucherNumber() : null,
                stmt.date(),
                book != null ? book.date() : null,
                stmt.description(),
                book != null ? book.narration() : null,
                stmt.reference(),
                stmt.debit(),
                stmt.credit(),
                book != null ? book.debit() : 0,
                book != null ? book.credit() : 0,
                book != null && book.reconciled(),
                note);
    }

    static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
