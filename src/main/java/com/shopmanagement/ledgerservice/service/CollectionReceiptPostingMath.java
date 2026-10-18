package com.shopmanagement.ledgerservice.service;

/**
 * Receipt posting for AR collections (wholesale SI or later POS/department collection).
 * Dr Cash (1000) or Bank (1010); Cr Debtors (1100). No GST — tax already sat on the original sale.
 */
public final class CollectionReceiptPostingMath {

    public static final String CODE_CASH = PosSalePostingMath.CODE_CASH;
    public static final String CODE_BANK = PosSalePostingMath.CODE_BANK;
    public static final String CODE_DEBTORS = PosSalePostingMath.CODE_DEBTORS;

    private CollectionReceiptPostingMath() {
    }

    public record ReceiptSplit(double amount, String cashBankCode) {
    }

    /** Collection amount only (not the original invoice). Cash → 1000; UPI/card/other → 1010. */
    public static ReceiptSplit split(double amount, String paymentMethod) {
        double collected = PosSalePostingMath.round2(Math.max(0, amount));
        return new ReceiptSplit(collected, PosSalePostingMath.cashBankCode(paymentMethod));
    }

    public static boolean isBalanced(double debit, double credit) {
        return VoucherService.isBalanced(debit, credit);
    }
}
