package com.shopmanagement.ledgerservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PurchaseDebitNotePostingMathTest {

    @Test
    void intraStateReversesCgstSgstAndStock() {
        PurchaseDebitNotePostingMath.DnVoucher dn =
                PurchaseDebitNotePostingMath.fromDocument(100.0, 18.0, 9.0, 9.0, 0.0, true);
        assertEquals(9.0, dn.split().cgst, 0.001);
        assertEquals(9.0, dn.split().sgst, 0.001);
        assertEquals(0.0, dn.split().igst, 0.001);
        assertEquals(0.0, dn.split().residual, 0.001);
        assertEquals("2000", dn.lines().get(0).code());
        assertEquals(118.0, dn.lines().get(0).debit(), 0.001);
        assertEquals("2210", dn.lines().get(1).code());
        assertEquals(9.0, dn.lines().get(1).credit(), 0.001);
        assertEquals("2220", dn.lines().get(2).code());
        assertEquals(9.0, dn.lines().get(2).credit(), 0.001);
        assertEquals("1200", dn.lines().get(3).code());
        assertEquals(100.0, dn.lines().get(3).credit(), 0.001);
        assertEquals(118.0, dn.creditorsDebit(), 0.001);
        assertEquals(100.0, dn.stockCredit(), 0.001);
        assertEquals(18.0, dn.inputGstCredit(), 0.001);
        assertTrue(PurchaseDebitNotePostingMath.isBalanced(dn.totalDebit(), dn.totalCredit()));
    }

    @Test
    void interStateReversesIgst() {
        PurchaseDebitNotePostingMath.DnVoucher dn =
                PurchaseDebitNotePostingMath.fromDocument(200.0, 36.0, 0.0, 0.0, 36.0, true);
        assertEquals(36.0, dn.split().igst, 0.001);
        assertEquals(0.0, dn.split().cgst, 0.001);
        assertEquals("2230", dn.lines().get(1).code());
        assertEquals(36.0, dn.lines().get(1).credit(), 0.001);
        assertEquals(236.0, dn.creditorsDebit(), 0.001);
        assertEquals(200.0, dn.stockCredit(), 0.001);
        assertFalse(dn.lines().stream().anyMatch(line -> "2210".equals(line.code()) || "2220".equals(line.code())));
    }

    @Test
    void taxOnlyStaysOnInputControl_noInventedSplit() {
        PurchaseDebitNotePostingMath.DnVoucher dn =
                PurchaseDebitNotePostingMath.fromDocument(50.0, 9.0, null, null, null, true);
        assertEquals(9.0, dn.split().residual, 0.001);
        assertEquals("2200", dn.lines().get(1).code());
        assertFalse(dn.lines().stream().anyMatch(line -> "2210".equals(line.code()) || "2220".equals(line.code())));
        assertEquals(59.0, dn.creditorsDebit(), 0.001);
        assertEquals(50.0, dn.stockCredit(), 0.001);
    }

    @Test
    void stockNotDoubledWhenReturnAlreadyPostedGoods() {
        PurchaseDebitNotePostingMath.DnVoucher dn =
                PurchaseDebitNotePostingMath.fromDocument(100.0, 18.0, 9.0, 9.0, 0.0, false);
        assertEquals(0.0, dn.stockCredit(), 0.001);
        assertEquals(18.0, dn.creditorsDebit(), 0.001);
        assertEquals(18.0, dn.inputGstCredit(), 0.001);
        assertFalse(dn.lines().stream().anyMatch(line -> "1200".equals(line.code())));
        assertEquals("2000", dn.lines().get(0).code());
        assertEquals(18.0, dn.lines().get(0).debit(), 0.001);
        assertTrue(PurchaseDebitNotePostingMath.isBalanced(dn.totalDebit(), dn.totalCredit()));
    }

    @Test
    void creditorsAndItcReversedAgainstGrnPlusItc() {
        ApItcPostingMath.ItcVoucher itc = ApItcPostingMath.fromDocument(18.0, 9.0, 9.0, 0.0);
        PurchaseDebitNotePostingMath.DnVoucher dn =
                PurchaseDebitNotePostingMath.fromDocument(100.0, 18.0, 9.0, 9.0, 0.0, true);
        PurchaseDebitNotePostingMath.CombinedBooks books =
                PurchaseDebitNotePostingMath.afterDebitNote(100.0, 100.0, itc.totalDebit(), itc.creditorsDelta(), dn);
        assertEquals(0.0, books.stock(), 0.001);
        assertEquals(0.0, books.creditors(), 0.001);
        assertEquals(0.0, books.inputGst(), 0.001);
    }
}
