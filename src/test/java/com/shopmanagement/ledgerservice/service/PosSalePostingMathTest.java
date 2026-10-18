package com.shopmanagement.ledgerservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PosSalePostingMathTest {

    @Test
    void paidCashHitsCashAccount() {
        PosSalePostingMath.DebitSplit split = PosSalePostingMath.split(118.0, 118.0, "CASH");
        assertEquals(118.0, split.cashOrBankDebit(), 0.001);
        assertEquals(0.0, split.debtorsDebit(), 0.001);
        assertEquals("1000", split.cashBankCode());
    }

    @Test
    void paidUpiHitsBankAccount() {
        PosSalePostingMath.DebitSplit split = PosSalePostingMath.split(118.0, 118.0, "UPI");
        assertEquals(118.0, split.cashOrBankDebit(), 0.001);
        assertEquals(0.0, split.debtorsDebit(), 0.001);
        assertEquals("1010", split.cashBankCode());
    }

    @Test
    void unpaidHitsDebtors() {
        PosSalePostingMath.DebitSplit split = PosSalePostingMath.split(118.0, 0.0, "CREDIT");
        assertEquals(0.0, split.cashOrBankDebit(), 0.001);
        assertEquals(118.0, split.debtorsDebit(), 0.001);
    }

    @Test
    void partialSplitsCashAndDebtors() {
        PosSalePostingMath.DebitSplit split = PosSalePostingMath.split(100.0, 40.0, "CASH");
        assertEquals(40.0, split.cashOrBankDebit(), 0.001);
        assertEquals(60.0, split.debtorsDebit(), 0.001);
        assertEquals(100.0, split.cashOrBankDebit() + split.debtorsDebit(), 0.001);
        assertEquals("1000", split.cashBankCode());
    }

    @Test
    void debitEqualsInvoiceTotal() {
        PosSalePostingMath.DebitSplit split = PosSalePostingMath.split(99.99, 33.33, "CARD");
        assertEquals(99.99, PosSalePostingMath.round2(split.cashOrBankDebit() + split.debtorsDebit()), 0.001);
    }

    @Test
    void cogsAddsEquallyToDebitAndCredit() {
        PosSalePostingMath.DebitSplit split = PosSalePostingMath.split(118.0, 118.0, "CASH");
        PosSalePostingMath.BalancedTotals totals = PosSalePostingMath.withCogs(split, 100.0, 18.0, 40.0);
        assertEquals(158.0, totals.debit(), 0.001);
        assertEquals(158.0, totals.credit(), 0.001);
    }

    @Test
    void missingCogsLeavesSalesSideUnchanged() {
        PosSalePostingMath.DebitSplit split = PosSalePostingMath.split(118.0, 0.0, "CREDIT");
        PosSalePostingMath.BalancedTotals totals = PosSalePostingMath.withCogs(split, 100.0, 18.0, 0.0);
        assertEquals(118.0, totals.debit(), 0.001);
        assertEquals(118.0, totals.credit(), 0.001);
    }
}
