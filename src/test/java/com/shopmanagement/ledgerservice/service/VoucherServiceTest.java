package com.shopmanagement.ledgerservice.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VoucherServiceTest {

    @Test
    void balancedTotalsPass() {
        assertTrue(VoucherService.isBalanced(100.0, 100.0));
        assertTrue(VoucherService.isBalanced(100.004, 100.0));
    }

    @Test
    void unbalancedTotalsFail() {
        assertFalse(VoucherService.isBalanced(100.0, 99.0));
    }
}
