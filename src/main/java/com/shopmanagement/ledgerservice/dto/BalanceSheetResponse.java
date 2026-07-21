package com.shopmanagement.ledgerservice.dto;

import java.time.LocalDate;
import java.util.List;

public record BalanceSheetResponse(
        LocalDate asOf,
        LocalDate financialYearStart,
        List<FinalAccountLine> assets,
        List<FinalAccountLine> liabilities,
        List<FinalAccountLine> equity,
        double totalAssets,
        double totalLiabilities,
        double totalEquity,
        double currentYearProfit,
        double liabilitiesAndEquity,
        boolean balanced,
        String note) {
}
