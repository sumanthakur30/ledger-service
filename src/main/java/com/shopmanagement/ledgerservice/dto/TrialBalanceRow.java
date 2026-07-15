package com.shopmanagement.ledgerservice.dto;

public record TrialBalanceRow(Long accountId, String code, String name, String accountType, double debit, double credit) {
}
