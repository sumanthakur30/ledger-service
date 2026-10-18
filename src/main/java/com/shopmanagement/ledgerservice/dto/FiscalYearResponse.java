package com.shopmanagement.ledgerservice.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record FiscalYearResponse(
        Long id,
        String code,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        boolean openingTransferred,
        Long closeVoucherId,
        LocalDateTime closedAt,
        String closedBy) {
}
