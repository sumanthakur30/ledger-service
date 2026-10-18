package com.shopmanagement.ledgerservice.service;

/** Balanced promotion expense: stock at cost plus traced input-tax reversal. */
public final class ItcReversalPostingMath {

    public static final String PROMOTION_EXPENSE = "5600";
    public static final String STOCK = "1200";

    private ItcReversalPostingMath() {
    }

    public record Posting(double cost, double cgst, double sgst, double igst, double cess, double tax, double total) {
    }

    public static Posting of(Double cost, Double cgst, Double sgst, Double igst, Double cess) {
        double stock = round2(Math.max(0, n(cost)));
        double c = round2(Math.max(0, n(cgst)));
        double s = round2(Math.max(0, n(sgst)));
        double i = round2(Math.max(0, n(igst)));
        double ce = round2(Math.max(0, n(cess)));
        double tax = round2(c + s + i + ce);
        return new Posting(stock, c, s, i, ce, tax, round2(stock + tax));
    }

    public static boolean balanced(Posting posting) {
        return Math.abs(posting.total() - round2(posting.cost() + posting.tax())) < 0.001;
    }

    private static double n(Double value) {
        return value == null ? 0 : value;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
