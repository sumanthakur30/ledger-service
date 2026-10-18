package com.shopmanagement.ledgerservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GstSplitTest {

    @Test
    void taxOnlyStaysOnControlLedger() {
        GstSplit split = GstSplit.of(118.0, null, null, null);
        assertEquals(0.0, split.cgst, 0.001);
        assertEquals(0.0, split.sgst, 0.001);
        assertEquals(0.0, split.igst, 0.001);
        assertEquals(118.0, split.residual, 0.001);
        assertEquals(118.0, split.total, 0.001);
    }

    @Test
    void sameStateUsesCgstSgst() {
        GstSplit split = GstSplit.of(18.0, 9.0, 9.0, 0.0);
        assertEquals(9.0, split.cgst, 0.001);
        assertEquals(9.0, split.sgst, 0.001);
        assertEquals(0.0, split.igst, 0.001);
        assertEquals(0.0, split.residual, 0.001);
        assertEquals(18.0, split.total, 0.001);
    }

    @Test
    void interstateUsesIgst() {
        GstSplit split = GstSplit.of(18.0, 0.0, 0.0, 18.0);
        assertEquals(18.0, split.igst, 0.001);
        assertEquals(0.0, split.residual, 0.001);
    }

    @Test
    void leftoverRoundingStaysOnControl() {
        GstSplit split = GstSplit.of(18.01, 9.0, 9.0, 0.0);
        assertEquals(0.01, split.residual, 0.001);
        assertEquals(18.01, split.total, 0.001);
    }

    @Test
    void cappedToInvoiceTotal() {
        GstSplit split = GstSplit.of(30.0, 9.0, 9.0, 18.0).cappedTo(18.0);
        assertEquals(18.0, split.total, 0.001);
    }

    @Test
    void proratePartialReturn() {
        GstSplit split = GstSplit.prorate(18.0, 9.0, 9.0, 0.0, 9.0);
        assertEquals(4.5, split.cgst, 0.001);
        assertEquals(4.5, split.sgst, 0.001);
        assertEquals(9.0, split.total, 0.001);
    }

    @Test
    void outputAndInputCodeSets() {
        assertTrue(GstLedgerCodes.isOutput("2110"));
        assertTrue(GstLedgerCodes.isOutput("2100"));
        assertTrue(GstLedgerCodes.isInput("2210"));
        assertTrue(GstLedgerCodes.isInput("2200"));
    }
}
