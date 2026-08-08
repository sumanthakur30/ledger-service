package com.shopmanagement.ledgerservice.dto;

import java.time.LocalDate;

/** Cash movement (1000/1010) vs accrual net profit for the same period. */
public record CashVsProfitResponse(
        LocalDate fromDate,
        LocalDate toDate,
        double cashIn,
        double cashOut,
        double netCashMovement,
        double accrualNetProfit,
        String note) {
}
