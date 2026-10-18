package com.shopmanagement.ledgerservice.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Purchase debit note after GRN + AP ITC.
 *
 * <p>Existing {@code PURCHASE_RETURN} ship only moves inventory qty and writes a procurement
 * outbox event — it does <em>not</em> post ledger Stock/Creditors. So a shipped return
 * must credit Stock 1200 for ex-tax goods (same valuation as GRN). Set {@code creditStock=false}
 * only when that GL stock credit already exists, so this voucher is tax/creditors only.
 *
 * <p>{@link GstSplit} from the DN document — never invents 50/50.
 */
public final class PurchaseDebitNotePostingMath {

    public static final String CODE_STOCK = "1200";
    public static final String CODE_CREDITORS = "2000";

    private PurchaseDebitNotePostingMath() {
    }

    public record Line(String code, double debit, double credit) {
    }

    public record DnVoucher(
            List<Line> lines,
            double totalDebit,
            double totalCredit,
            double stockCredit,
            double creditorsDebit,
            double inputGstCredit,
            GstSplit split) {
    }

    public record CombinedBooks(double stock, double creditors, double inputGst) {
    }

    public static DnVoucher fromDocument(
            Double stockAmount,
            Double taxAmount,
            Double cgst,
            Double sgst,
            Double igst,
            boolean creditStock) {
        GstSplit split = GstSplit.of(taxAmount, cgst, sgst, igst);
        double goods = creditStock ? GstSplit.round2(Math.max(0, n(stockAmount))) : 0;
        double tax = split.total;
        double creditors = GstSplit.round2(goods + tax);

        List<Line> lines = new ArrayList<>();
        if (creditors > 0.009) {
            lines.add(new Line(CODE_CREDITORS, creditors, 0));
        }
        addCredit(lines, GstLedgerCodes.INPUT_CGST, split.cgst);
        addCredit(lines, GstLedgerCodes.INPUT_SGST, split.sgst);
        addCredit(lines, GstLedgerCodes.INPUT_IGST, split.igst);
        addCredit(lines, GstLedgerCodes.INPUT_CREDIT, split.residual);
        if (goods > 0.009) {
            lines.add(new Line(CODE_STOCK, 0, goods));
        }
        return new DnVoucher(List.copyOf(lines), creditors, creditors, goods, creditors, tax, split);
    }

    /**
     * Books after GRN (ex-tax) + AP ITC (tax) + this DN.
     * {@code itcCreditors} / {@code itcInput} are the AP_ITC tax-only deltas.
     */
    public static CombinedBooks afterDebitNote(
            double grnStock,
            double grnCreditors,
            double itcInput,
            double itcCreditors,
            DnVoucher dn) {
        return new CombinedBooks(
                GstSplit.round2(grnStock - dn.stockCredit()),
                GstSplit.round2(grnCreditors + itcCreditors - dn.creditorsDebit()),
                GstSplit.round2(itcInput - dn.inputGstCredit()));
    }

    public static boolean isBalanced(double debit, double credit) {
        return VoucherService.isBalanced(debit, credit);
    }

    private static void addCredit(List<Line> lines, String code, double amount) {
        if (amount <= 0.009) {
            return;
        }
        lines.add(new Line(code, 0, GstSplit.round2(amount)));
    }

    private static double n(Double value) {
        return value == null ? 0d : value;
    }
}
