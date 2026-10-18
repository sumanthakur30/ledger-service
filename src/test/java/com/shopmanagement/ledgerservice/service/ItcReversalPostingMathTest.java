package com.shopmanagement.ledgerservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ItcReversalPostingMathTest {

    @Test
    void costAndTracedTaxBalance() {
        ItcReversalPostingMath.Posting posting = ItcReversalPostingMath.of(100.0, 6.0, 6.0, null, null);
        assertEquals(100.0, posting.cost());
        assertEquals(12.0, posting.tax());
        assertEquals(112.0, posting.total());
        assertTrue(ItcReversalPostingMath.balanced(posting));
    }

    @Test
    void igstIsNotSplitIntoCgst() {
        ItcReversalPostingMath.Posting posting = ItcReversalPostingMath.of(50.0, null, null, 9.0, null);
        assertEquals(0.0, posting.cgst());
        assertEquals(9.0, posting.igst());
        assertEquals(59.0, posting.total());
    }
}
