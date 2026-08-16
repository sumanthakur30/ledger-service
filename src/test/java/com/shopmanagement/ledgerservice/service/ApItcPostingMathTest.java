package com.shopmanagement.ledgerservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ApItcPostingMathTest {

    @Test
    void intraStateUsesDocumentCgstSgst() {
        ApItcPostingMath.ItcVoucher itc = ApItcPostingMath.fromDocument(18.0, 9.0, 9.0, 0.0);
        assertEquals(9.0, itc.split().cgst, 0.001);
        assertEquals(9.0, itc.split().sgst, 0.001);
        assertEquals(0.0, itc.split().igst, 0.001);
        assertEquals(0.0, itc.split().residual, 0.001);
        assertEquals("2210", itc.lines().get(0).code());
        assertEquals("2220", itc.lines().get(1).code());
        assertEquals("2000", itc.lines().get(2).code());
        assertEquals(18.0, itc.totalDebit(), 0.001);
        assertEquals(18.0, itc.creditorsDelta(), 0.001);
        assertEquals(0.0, itc.stockDelta(), 0.001);
        assertTrue(ApItcPostingMath.isBalanced(itc.totalDebit(), itc.totalCredit()));
    }

    @Test
    void interStateUsesDocumentIgst() {
        ApItcPostingMath.ItcVoucher itc = ApItcPostingMath.fromDocument(18.0, 0.0, 0.0, 18.0);
        assertEquals(18.0, itc.split().igst, 0.001);
        assertEquals(0.0, itc.split().cgst, 0.001);
        assertEquals(0.0, itc.split().sgst, 0.001);
        assertEquals("2230", itc.lines().get(0).code());
        assertEquals("2000", itc.lines().get(1).code());
        assertEquals(18.0, itc.creditorsDelta(), 0.001);
        assertEquals(0.0, itc.stockDelta(), 0.001);
    }

    @Test
    void taxOnlyStaysOnInputControl_noInventedSplit() {
        ApItcPostingMath.ItcVoucher itc = ApItcPostingMath.fromDocument(18.0, null, null, null);
        assertEquals(0.0, itc.split().cgst, 0.001);
        assertEquals(0.0, itc.split().sgst, 0.001);
        assertEquals(0.0, itc.split().igst, 0.001);
        assertEquals(18.0, itc.split().residual, 0.001);
        assertEquals("2200", itc.lines().get(0).code());
        assertEquals("2000", itc.lines().get(1).code());
        assertFalse(itc.lines().stream().anyMatch(line -> "2210".equals(line.code()) || "2220".equals(line.code())));
    }

    @Test
    void grnStockUnchangedWhenItcPosts() {
        ApItcPostingMath.ItcVoucher itc = ApItcPostingMath.fromDocument(18.0, 9.0, 9.0, 0.0);
        ApItcPostingMath.CombinedBooks books = ApItcPostingMath.afterItc(100.0, 100.0, itc);
        assertEquals(100.0, books.stock(), 0.001);
        assertEquals(118.0, books.creditors(), 0.001);
        assertEquals(18.0, books.inputGst(), 0.001);
    }

    @Test
    void creditorsNotDoubledForSameTax() {
        ApItcPostingMath.ItcVoucher itc = ApItcPostingMath.fromDocument(18.0, 0.0, 0.0, 18.0);
        ApItcPostingMath.CombinedBooks books = ApItcPostingMath.afterItc(200.0, 200.0, itc);
        assertEquals(218.0, books.creditors(), 0.001);
        assertEquals(200.0, books.stock(), 0.001);
        assertEquals(18.0, itc.creditorsDelta(), 0.001);
    }
}
