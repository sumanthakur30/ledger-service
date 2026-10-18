package com.shopmanagement.ledgerservice.service;

import java.time.LocalDate;
import java.util.Locale;

/** Indian financial-year helpers (1 Apr – 31 Mar). */
public final class FiscalYearMath {

    private FiscalYearMath() {
    }

    public record Range(LocalDate start, LocalDate end) {
    }

    public static Range indianRangeContaining(LocalDate asOf) {
        LocalDate date = asOf != null ? asOf : LocalDate.now();
        LocalDate start = date.getMonthValue() >= 4
                ? LocalDate.of(date.getYear(), 4, 1)
                : LocalDate.of(date.getYear() - 1, 4, 1);
        return new Range(start, start.plusYears(1).minusDays(1));
    }

    public static String codeFor(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("start and end dates are required");
        }
        return start.getYear() + "-" + String.format(Locale.ROOT, "%02d", end.getYear() % 100);
    }

    public static String displayName(String code) {
        return "FY " + code;
    }

    public static boolean overlaps(LocalDate aStart, LocalDate aEnd, LocalDate bStart, LocalDate bEnd) {
        return !aEnd.isBefore(bStart) && !bEnd.isBefore(aStart);
    }

    public static boolean contains(LocalDate start, LocalDate end, LocalDate date) {
        return date != null && !date.isBefore(start) && !date.isAfter(end);
    }

    public static Range nextRange(LocalDate start, LocalDate end) {
        LocalDate nextStart = end.plusDays(1);
        LocalDate nextEnd = nextStart.plusYears(1).minusDays(1);
        return new Range(nextStart, nextEnd);
    }
}
