package com.shopmanagement.ledgerservice.dto;

/** One line on P&amp;L or Balance Sheet (signed amount in account-normal direction). */
public record FinalAccountLine(
        Long accountId,
        String code,
        String name,
        String accountType,
        double amount) {
}
