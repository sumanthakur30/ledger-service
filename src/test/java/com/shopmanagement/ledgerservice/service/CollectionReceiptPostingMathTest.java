package com.shopmanagement.ledgerservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CollectionReceiptPostingMathTest {

    @Test
    void cashHitsCashAccount() {
        CollectionReceiptPostingMath.ReceiptSplit split = CollectionReceiptPostingMath.split(1000.0, "CASH");
        assertEquals(1000.0, split.amount(), 0.001);
        assertEquals("1000", split.cashBankCode());
        assertTrue(CollectionReceiptPostingMath.isBalanced(split.amount(), split.amount()));
    }

    @Test
    void upiHitsBankAccount() {
        CollectionReceiptPostingMath.ReceiptSplit split = CollectionReceiptPostingMath.split(500.0, "UPI");
        assertEquals(500.0, split.amount(), 0.001);
        assertEquals("1010", split.cashBankCode());
    }

    @Test
    void cardHitsBankAccount() {
        CollectionReceiptPostingMath.ReceiptSplit split = CollectionReceiptPostingMath.split(250.0, "CARD");
        assertEquals("1010", split.cashBankCode());
        assertTrue(CollectionReceiptPostingMath.isBalanced(split.amount(), split.amount()));
    }

    @Test
    void partialCollectionIsThisReceiptNotTheInvoice() {
        CollectionReceiptPostingMath.ReceiptSplit split = CollectionReceiptPostingMath.split(40.0, "CASH");
        assertEquals(40.0, split.amount(), 0.001);
        assertEquals("1000", split.cashBankCode());
        assertTrue(CollectionReceiptPostingMath.isBalanced(40.0, 40.0));
    }

    @Test
    void secondPartialCollectionIsItsOwnAmount() {
        CollectionReceiptPostingMath.ReceiptSplit first = CollectionReceiptPostingMath.split(40.0, "CASH");
        CollectionReceiptPostingMath.ReceiptSplit second = CollectionReceiptPostingMath.split(60.0, "UPI");
        assertEquals(40.0, first.amount(), 0.001);
        assertEquals(60.0, second.amount(), 0.001);
        assertEquals("1000", first.cashBankCode());
        assertEquals("1010", second.cashBankCode());
    }
}
