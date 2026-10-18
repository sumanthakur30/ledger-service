package com.shopmanagement.ledgerservice.service;

import java.util.Locale;

/**
 * Debit split for POS / department bills: cash/bank for the paid portion, debtors for the rest.
 * Credits (sales + GST) are applied by {@link PosSaleVoucherService}.
 */
public final class PosSalePostingMath {

    public static final String CODE_CASH = "1000";
    public static final String CODE_BANK = "1010";
    public static final String CODE_DEBTORS = "1100";

    private PosSalePostingMath() {
    }

    public record DebitSplit(double cashOrBankDebit, double debtorsDebit, String cashBankCode) {
    }

    public static DebitSplit split(double total, double paidAmount, String paymentMethod) {
        double totalR = round2(Math.max(0, total));
        double paid = round2(Math.max(0, Math.min(paidAmount, totalR)));
        String code = cashBankCode(paymentMethod);
        if (totalR <= 0.009) {
            return new DebitSplit(0, 0, code);
        }
        if (paid <= 0.009) {
            return new DebitSplit(0, totalR, code);
        }
        if (paid >= round2(totalR - 0.009)) {
            return new DebitSplit(totalR, 0, code);
        }
        double cash = paid;
        double debtors = round2(totalR - cash);
        return new DebitSplit(cash, debtors, code);
    }

    public static String cashBankCode(String paymentMethod) {
        String method = paymentMethod != null ? paymentMethod.trim().toUpperCase(Locale.ROOT) : "CASH";
        return "CASH".equals(method) ? CODE_CASH : CODE_BANK;
    }

    public record BalancedTotals(double debit, double credit) {
    }

    /** Debits (cash/bank/AR + COGS) and credits (sales + GST + stock). */
    public static BalancedTotals withCogs(DebitSplit split, double salesCredit, double tax, double cogsAmount) {
        double cogs = round2(Math.max(0, cogsAmount));
        double debit = round2(split.cashOrBankDebit() + split.debtorsDebit() + cogs);
        double credit = round2(salesCredit + tax + cogs);
        return new BalancedTotals(debit, credit);
    }

    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
