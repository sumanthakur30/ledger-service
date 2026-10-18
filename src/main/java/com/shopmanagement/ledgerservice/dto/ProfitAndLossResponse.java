package com.shopmanagement.ledgerservice.dto;

import java.time.LocalDate;
import java.util.List;

public record ProfitAndLossResponse(
        LocalDate fromDate,
        LocalDate toDate,
        List<FinalAccountLine> income,
        List<FinalAccountLine> expenses,
        double totalIncome,
        double totalExpenses,
        double netProfit,
        double salesNetCredit,
        double openingStock,
        double closingStock,
        double stockIncrease,
        String note) {
}
