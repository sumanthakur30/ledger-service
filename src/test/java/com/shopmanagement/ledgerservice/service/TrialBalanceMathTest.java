package com.shopmanagement.ledgerservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.shopmanagement.ledgerservice.dto.TrialBalanceRow;

class TrialBalanceMathTest {

    private static final TrialBalanceMath.AccountSeed CASH =
            new TrialBalanceMath.AccountSeed(1L, "1000", "Cash", "ASSET");
    private static final TrialBalanceMath.AccountSeed SALES =
            new TrialBalanceMath.AccountSeed(2L, "4000", "Sales", "INCOME");

    @Test
    void normalizeDoesNotInventBranch() {
        assertNull(TrialBalanceMath.normalize(null));
        assertNull(TrialBalanceMath.normalize(0L));
        assertNull(TrialBalanceMath.normalize(-1L));
        assertEquals(7L, TrialBalanceMath.normalize(7L));
    }

    @Test
    void consolidatedIncludesEveryBranchAndNull() {
        assertTrue(TrialBalanceMath.includeVoucher(10L, null));
        assertTrue(TrialBalanceMath.includeVoucher(20L, null));
        assertTrue(TrialBalanceMath.includeVoucher(null, null));
    }

    @Test
    void branchFilterExcludesOtherBranchesAndNull() {
        assertTrue(TrialBalanceMath.includeVoucher(10L, 10L));
        assertFalse(TrialBalanceMath.includeVoucher(20L, 10L));
        assertFalse(TrialBalanceMath.includeVoucher(null, 10L));
    }

    @Test
    void accumulateBranchVsConsolidated() {
        List<TrialBalanceMath.PostedVoucher> vouchers = List.of(
                posted(10L, 100, 100),
                posted(20L, 50, 50),
                posted(null, 25, 25));

        List<TrialBalanceRow> consolidated = TrialBalanceMath.accumulate(
                List.of(CASH, SALES), vouchers, null, false);
        assertEquals(175.0, debit(consolidated, "1000"), 0.001);
        assertEquals(175.0, credit(consolidated, "4000"), 0.001);

        List<TrialBalanceRow> branchA = TrialBalanceMath.accumulate(
                List.of(CASH, SALES), vouchers, 10L, false);
        assertEquals(100.0, debit(branchA, "1000"), 0.001);
        assertEquals(100.0, credit(branchA, "4000"), 0.001);

        List<TrialBalanceRow> branchB = TrialBalanceMath.accumulate(
                List.of(CASH, SALES), vouchers, 20L, false);
        assertEquals(50.0, debit(branchB, "1000"), 0.001);
        assertEquals(50.0, credit(branchB, "4000"), 0.001);
    }

    @Test
    void draftAndYearEndExcludedWhenAsked() {
        List<TrialBalanceMath.PostedVoucher> vouchers = List.of(
                new TrialBalanceMath.PostedVoucher(
                        10L, "DRAFT", "JOURNAL", null, List.of(new TrialBalanceMath.Line(1L, 99, 0))),
                new TrialBalanceMath.PostedVoucher(
                        10L, "POSTED", "YEAR_END", "FY_CLOSE", List.of(new TrialBalanceMath.Line(1L, 40, 0))),
                posted(10L, 10, 10));

        List<TrialBalanceRow> movement = TrialBalanceMath.accumulate(
                List.of(CASH, SALES), vouchers, 10L, true);
        assertEquals(10.0, debit(movement, "1000"), 0.001);
    }

    private static TrialBalanceMath.PostedVoucher posted(Long branchId, double debit, double credit) {
        return new TrialBalanceMath.PostedVoucher(
                branchId,
                "POSTED",
                "SALES",
                "POS_SALE",
                List.of(
                        new TrialBalanceMath.Line(1L, debit, 0),
                        new TrialBalanceMath.Line(2L, 0, credit)));
    }

    private static double debit(List<TrialBalanceRow> rows, String code) {
        return rows.stream().filter(r -> code.equals(r.code())).mapToDouble(TrialBalanceRow::debit).findFirst().orElse(0);
    }

    private static double credit(List<TrialBalanceRow> rows, String code) {
        return rows.stream().filter(r -> code.equals(r.code())).mapToDouble(TrialBalanceRow::credit).findFirst().orElse(0);
    }
}
