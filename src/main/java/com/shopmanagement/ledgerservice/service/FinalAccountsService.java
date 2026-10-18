package com.shopmanagement.ledgerservice.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.ledgerservice.dto.BalanceSheetResponse;
import com.shopmanagement.ledgerservice.dto.FinalAccountLine;
import com.shopmanagement.ledgerservice.dto.ProfitAndLossResponse;
import com.shopmanagement.ledgerservice.dto.TrialBalanceRow;
import com.shopmanagement.ledgerservice.filter.RequestIdFilter;

/**
 * Final accounts from the existing trade GL (posted vouchers only). No parallel books.
 * <p>
 * Purchases currently capitalize into Stock (1200), so EXPENSE rarely holds COGS — P&amp;L
 * surfaces a stock-increase memo until COGS journals exist.
 */
@Service
public class FinalAccountsService {

    private static final String NOTE_PNL =
            "GL operating P&L from INCOME/EXPENSE accounts. COGS (5300) posts on wholesale invoices and POS bills when "
                    + "batch cost is known. Purchases still capitalize to Stock (1200) at GRN; stock Δ memo remains "
                    + "for trading context.";
    private static final String NOTE_BS =
            "Balances from trial balance as of date. Current-year P&L (shop financial year, default Apr–Mar) "
                    + "is plugged into equity so the sheet can balance. Closed years use retained earnings (3100).";

    private final VoucherService voucherService;
    private final FiscalYearService fiscalYearService;

    public FinalAccountsService(VoucherService voucherService, FiscalYearService fiscalYearService) {
        this.voucherService = voucherService;
        this.fiscalYearService = fiscalYearService;
    }

    @Transactional(readOnly = true)
    public ProfitAndLossResponse profitAndLoss(LocalDate from, LocalDate to) {
        return profitAndLoss(from, to, null);
    }

    @Transactional(readOnly = true)
    public ProfitAndLossResponse profitAndLoss(LocalDate from, LocalDate to, Long branchId) {
        requireManageOrders();
        LocalDate fromDate = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate toDate = to != null ? to : LocalDate.now();
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate must be on or before toDate");
        }

        List<TrialBalanceRow> movement = voucherService.periodMovement(fromDate, toDate, branchId);
        List<FinalAccountLine> income = new ArrayList<>();
        List<FinalAccountLine> expenses = new ArrayList<>();
        double totalIncome = 0;
        double totalExpenses = 0;
        double salesNetCredit = 0;

        for (TrialBalanceRow row : movement) {
            String type = normalizeType(row.accountType());
            double amount = signedNet(row);
            if (Math.abs(amount) < 0.009) {
                continue;
            }
            if ("INCOME".equals(type)) {
                income.add(line(row, amount));
                totalIncome = round2(totalIncome + amount);
                if ("4000".equals(row.code())) {
                    salesNetCredit = amount;
                }
            } else if ("EXPENSE".equals(type)) {
                expenses.add(line(row, amount));
                totalExpenses = round2(totalExpenses + amount);
            }
        }

        LocalDate dayBefore = fromDate.minusDays(1);
        double openingStock = assetNet(voucherService.trialBalance(dayBefore, branchId), "1200");
        double closingStock = assetNet(voucherService.trialBalance(toDate, branchId), "1200");
        double stockIncrease = round2(closingStock - openingStock);
        double netProfit = round2(totalIncome - totalExpenses);

        return new ProfitAndLossResponse(
                fromDate,
                toDate,
                income,
                expenses,
                round2(totalIncome),
                round2(totalExpenses),
                netProfit,
                round2(salesNetCredit),
                round2(openingStock),
                round2(closingStock),
                stockIncrease,
                NOTE_PNL);
    }

    @Transactional(readOnly = true)
    public BalanceSheetResponse balanceSheet(LocalDate asOf) {
        return balanceSheet(asOf, null);
    }

    @Transactional(readOnly = true)
    public BalanceSheetResponse balanceSheet(LocalDate asOf, Long branchId) {
        requireManageOrders();
        LocalDate cutoff = asOf != null ? asOf : LocalDate.now();
        var covering = fiscalYearService.findCovering(cutoff);
        LocalDate fyStart = covering.map(y -> y.getStartDate()).orElseGet(() -> indianFyStart(cutoff));
        boolean plugCurrentYear = covering.isEmpty() || !covering.get().isClosed();

        List<TrialBalanceRow> tb = voucherService.trialBalance(cutoff, branchId);
        List<FinalAccountLine> assets = new ArrayList<>();
        List<FinalAccountLine> liabilities = new ArrayList<>();
        List<FinalAccountLine> equity = new ArrayList<>();
        double totalAssets = 0;
        double totalLiabilities = 0;
        double totalEquity = 0;

        for (TrialBalanceRow row : tb) {
            String type = normalizeType(row.accountType());
            double amount = signedNet(row);
            if (Math.abs(amount) < 0.009) {
                continue;
            }
            FinalAccountLine line = line(row, amount);
            switch (type) {
                case "ASSET" -> {
                    assets.add(line);
                    totalAssets = round2(totalAssets + amount);
                }
                case "LIABILITY" -> {
                    liabilities.add(line);
                    totalLiabilities = round2(totalLiabilities + amount);
                }
                case "EQUITY" -> {
                    equity.add(line);
                    totalEquity = round2(totalEquity + amount);
                }
                default -> {
                    // INCOME/EXPENSE belong on P&L, not BS body
                }
            }
        }

        ProfitAndLossResponse ytd = profitAndLoss(fyStart, cutoff, branchId);
        double currentYearProfit = ytd.netProfit();
        if (plugCurrentYear && Math.abs(currentYearProfit) >= 0.009) {
            equity.add(new FinalAccountLine(
                    null,
                    "PL",
                    "Profit & Loss A/c (current year)",
                    "EQUITY",
                    currentYearProfit));
            totalEquity = round2(totalEquity + currentYearProfit);
        }

        double liabilitiesAndEquity = round2(totalLiabilities + totalEquity);
        boolean balanced = Math.abs(totalAssets - liabilitiesAndEquity) <= 0.05;

        return new BalanceSheetResponse(
                cutoff,
                fyStart,
                assets,
                liabilities,
                equity,
                round2(totalAssets),
                round2(totalLiabilities),
                round2(totalEquity),
                currentYearProfit,
                liabilitiesAndEquity,
                balanced,
                NOTE_BS);
    }

    private static FinalAccountLine line(TrialBalanceRow row, double amount) {
        return new FinalAccountLine(row.accountId(), row.code(), row.name(), row.accountType(), round2(amount));
    }

    private static double assetNet(List<TrialBalanceRow> tb, String code) {
        for (TrialBalanceRow row : tb) {
            if (code.equals(row.code())) {
                return signedNet(row);
            }
        }
        return 0;
    }

    /** Debit-normal for ASSET/EXPENSE; credit-normal for LIABILITY/INCOME/EQUITY. */
    static double signedNet(TrialBalanceRow row) {
        double debit = safe(row.debit());
        double credit = safe(row.credit());
        String type = normalizeType(row.accountType());
        if ("ASSET".equals(type) || "EXPENSE".equals(type)) {
            return round2(debit - credit);
        }
        return round2(credit - debit);
    }

    static LocalDate indianFyStart(LocalDate asOf) {
        if (asOf.getMonthValue() >= 4) {
            return LocalDate.of(asOf.getYear(), 4, 1);
        }
        return LocalDate.of(asOf.getYear() - 1, 4, 1);
    }

    private static String normalizeType(String accountType) {
        return accountType == null ? "" : accountType.trim().toUpperCase(Locale.ROOT);
    }

    private static double safe(Double value) {
        return value == null ? 0.0 : value;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private void requireManageOrders() {
        String role = RequestIdFilter.getCurrentRole();
        if ("SUPER_ADMIN".equals(role) || "SHOP_OWNER".equals(role)) {
            return;
        }
        if (!RequestIdFilter.getCurrentPermissions().contains("MANAGE_ORDERS")) {
            throw new SecurityException("Forbidden: missing permission MANAGE_ORDERS");
        }
    }
}
