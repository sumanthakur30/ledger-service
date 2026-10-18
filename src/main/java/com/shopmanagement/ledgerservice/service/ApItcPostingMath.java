package com.shopmanagement.ledgerservice.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Purchase ITC after GRN — chosen offset so Stock and Creditors are not double-counted.
 *
 * <p>Live GRN payload ({@code from-goods-receipt}) is Dr 1200 / Cr 2000 at ex-tax landed
 * cost ({@code purchasePrice} = PO unit cost + allocated freight). The GRN DTO has no GST
 * fields and {@code GoodsReceiptVoucherService} does not post 2210/2220/2230.
 *
 * <p>Therefore document GST is <em>not</em> already in Stock or on the GRN Creditors line.
 * Safe ITC: Dr Input CGST/SGST/IGST (or 2200 residual) / Cr Creditors 2000 for tax only.
 * Do <em>not</em> Cr Stock (would change GRN valuation) and do <em>not</em> re-credit the
 * GRN goods amount. Uses {@link GstSplit} — never invents a 50/50 CGST/SGST split.
 */
public final class ApItcPostingMath {

    public static final String CODE_STOCK = "1200";
    public static final String CODE_CREDITORS = "2000";

    private ApItcPostingMath() {
    }

    public record Line(String code, double debit, double credit) {
    }

    public record ItcVoucher(
            List<Line> lines,
            double totalDebit,
            double totalCredit,
            double stockDelta,
            double creditorsDelta,
            GstSplit split) {
    }

    public record CombinedBooks(double stock, double creditors, double inputGst) {
    }

    public static ItcVoucher fromDocument(Double taxAmount, Double cgst, Double sgst, Double igst) {
        GstSplit split = GstSplit.of(taxAmount, cgst, sgst, igst);
        List<Line> lines = new ArrayList<>();
        addDebit(lines, GstLedgerCodes.INPUT_CGST, split.cgst);
        addDebit(lines, GstLedgerCodes.INPUT_SGST, split.sgst);
        addDebit(lines, GstLedgerCodes.INPUT_IGST, split.igst);
        addDebit(lines, GstLedgerCodes.INPUT_CREDIT, split.residual);
        if (split.total > 0.009) {
            lines.add(new Line(CODE_CREDITORS, 0, split.total));
        }
        return new ItcVoucher(List.copyOf(lines), split.total, split.total, 0, split.total, split);
    }

    /** GRN stock stays at landed ex-tax; creditors grow only by tax not on the GRN voucher. */
    public static CombinedBooks afterItc(double grnStock, double grnCreditors, ItcVoucher itc) {
        return new CombinedBooks(
                GstSplit.round2(grnStock + itc.stockDelta()),
                GstSplit.round2(grnCreditors + itc.creditorsDelta()),
                itc.totalDebit());
    }

    public static boolean isBalanced(double debit, double credit) {
        return VoucherService.isBalanced(debit, credit);
    }

    private static void addDebit(List<Line> lines, String code, double amount) {
        if (amount <= 0.009) {
            return;
        }
        lines.add(new Line(code, GstSplit.round2(amount), 0));
    }
}
