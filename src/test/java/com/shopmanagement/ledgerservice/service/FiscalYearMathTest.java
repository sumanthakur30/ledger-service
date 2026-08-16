package com.shopmanagement.ledgerservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class FiscalYearMathTest {

    @Test
    void indianRangeForAugust2026() {
        FiscalYearMath.Range range = FiscalYearMath.indianRangeContaining(LocalDate.of(2026, 8, 16));
        assertEquals(LocalDate.of(2026, 4, 1), range.start());
        assertEquals(LocalDate.of(2027, 3, 31), range.end());
        assertEquals("2026-27", FiscalYearMath.codeFor(range.start(), range.end()));
    }

    @Test
    void indianRangeForJanuaryUsesPreviousApril() {
        FiscalYearMath.Range range = FiscalYearMath.indianRangeContaining(LocalDate.of(2027, 1, 15));
        assertEquals(LocalDate.of(2026, 4, 1), range.start());
        assertEquals(LocalDate.of(2027, 3, 31), range.end());
    }

    @Test
    void nextRangeFollowsClosedYear() {
        FiscalYearMath.Range next = FiscalYearMath.nextRange(
                LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31));
        assertEquals(LocalDate.of(2027, 4, 1), next.start());
        assertEquals(LocalDate.of(2028, 3, 31), next.end());
        assertEquals("2027-28", FiscalYearMath.codeFor(next.start(), next.end()));
    }

    @Test
    void containsAndOverlap() {
        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end = LocalDate.of(2027, 3, 31);
        assertTrue(FiscalYearMath.contains(start, end, LocalDate.of(2026, 8, 16)));
        assertFalse(FiscalYearMath.contains(start, end, LocalDate.of(2027, 4, 1)));
        assertTrue(FiscalYearMath.overlaps(start, end, LocalDate.of(2027, 3, 1), LocalDate.of(2027, 6, 30)));
        assertFalse(FiscalYearMath.overlaps(start, end, LocalDate.of(2027, 4, 1), LocalDate.of(2028, 3, 31)));
    }

    @Test
    void voucherPrefixes() {
        assertEquals("JV", FiscalYearService.shortPrefix("JOURNAL"));
        assertEquals("YE", FiscalYearService.shortPrefix("YEAR_END"));
    }
}
