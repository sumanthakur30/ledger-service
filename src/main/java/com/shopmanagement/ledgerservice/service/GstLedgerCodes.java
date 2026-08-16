package com.shopmanagement.ledgerservice.service;

import java.util.function.Function;

import com.shopmanagement.ledgerservice.model.LedgerAccount;
import com.shopmanagement.ledgerservice.model.LedgerVoucher;
import com.shopmanagement.ledgerservice.model.LedgerVoucherLine;

public final class GstLedgerCodes {
    public static final String OUTPUT_PAYABLE = "2100";
    public static final String OUTPUT_CGST = "2110";
    public static final String OUTPUT_SGST = "2120";
    public static final String OUTPUT_IGST = "2130";
    public static final String INPUT_CREDIT = "2200";
    public static final String INPUT_CGST = "2210";
    public static final String INPUT_SGST = "2220";
    public static final String INPUT_IGST = "2230";

    private GstLedgerCodes() {
    }

    public static boolean isOutput(String code) {
        return OUTPUT_PAYABLE.equals(code)
                || OUTPUT_CGST.equals(code)
                || OUTPUT_SGST.equals(code)
                || OUTPUT_IGST.equals(code);
    }

    public static boolean isInput(String code) {
        return INPUT_CREDIT.equals(code)
                || INPUT_CGST.equals(code)
                || INPUT_SGST.equals(code)
                || INPUT_IGST.equals(code);
    }

    public static int creditOutput(
            LedgerVoucher voucher,
            Function<String, LedgerAccount> account,
            GstSplit split,
            String hint,
            int lineNo) {
        lineNo = add(voucher, account, OUTPUT_CGST, 0, split.cgst, "Output CGST " + hint, lineNo);
        lineNo = add(voucher, account, OUTPUT_SGST, 0, split.sgst, "Output SGST " + hint, lineNo);
        lineNo = add(voucher, account, OUTPUT_IGST, 0, split.igst, "Output IGST " + hint, lineNo);
        return add(voucher, account, OUTPUT_PAYABLE, 0, split.residual, "GST " + hint, lineNo);
    }

    public static int debitOutput(
            LedgerVoucher voucher,
            Function<String, LedgerAccount> account,
            GstSplit split,
            String hint,
            int lineNo) {
        lineNo = add(voucher, account, OUTPUT_CGST, split.cgst, 0, "Reverse CGST " + hint, lineNo);
        lineNo = add(voucher, account, OUTPUT_SGST, split.sgst, 0, "Reverse SGST " + hint, lineNo);
        lineNo = add(voucher, account, OUTPUT_IGST, split.igst, 0, "Reverse IGST " + hint, lineNo);
        return add(voucher, account, OUTPUT_PAYABLE, split.residual, 0, "Reverse GST " + hint, lineNo);
    }

    public static int debitInput(
            LedgerVoucher voucher,
            Function<String, LedgerAccount> account,
            GstSplit split,
            String hint,
            int lineNo) {
        lineNo = add(voucher, account, INPUT_CGST, split.cgst, 0, "Input CGST " + hint, lineNo);
        lineNo = add(voucher, account, INPUT_SGST, split.sgst, 0, "Input SGST " + hint, lineNo);
        lineNo = add(voucher, account, INPUT_IGST, split.igst, 0, "Input IGST " + hint, lineNo);
        return add(voucher, account, INPUT_CREDIT, split.residual, 0, "GST input " + hint, lineNo);
    }

    private static int add(
            LedgerVoucher voucher,
            Function<String, LedgerAccount> account,
            String code,
            double debit,
            double credit,
            String narration,
            int lineNo) {
        if (debit <= 0.009 && credit <= 0.009) {
            return lineNo;
        }
        LedgerAccount ledgerAccount = account.apply(code);
        LedgerVoucherLine line = new LedgerVoucherLine();
        line.setAccountId(ledgerAccount.getId());
        line.setDebit(GstSplit.round2(debit));
        line.setCredit(GstSplit.round2(credit));
        line.setLineNarration(narration);
        line.setLineNo(lineNo);
        voucher.addLine(line);
        return lineNo + 1;
    }
}
