package com.shopmanagement.ledgerservice.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.ledgerservice.filter.RequestIdFilter;
import com.shopmanagement.ledgerservice.model.LedgerAccount;
import com.shopmanagement.ledgerservice.model.LedgerVoucher;
import com.shopmanagement.ledgerservice.model.LedgerVoucherLine;
import com.shopmanagement.ledgerservice.model.ShopFiscalYear;
import com.shopmanagement.ledgerservice.repository.LedgerAccountRepository;
import com.shopmanagement.ledgerservice.repository.LedgerVoucherRepository;
import com.shopmanagement.ledgerservice.repository.ShopFiscalYearRepository;

/**
 * Year-end close: transfer current-year P&amp;L into retained earnings (3100).
 * Perpetual asset/liability balances carry forward — no duplicate opening books.
 * P&amp;L reports exclude {@code YEAR_END} so historical profit remains visible.
 */
@Service
public class FiscalYearCloseService {

    public static final String SOURCE_FY_CLOSE = "FY_CLOSE";
    public static final String CODE_RETAINED_EARNINGS = "3100";

    private final LedgerVoucherRepository voucherRepository;
    private final LedgerAccountRepository accountRepository;
    private final ChartOfAccountsService chartOfAccountsService;
    private final ShopFiscalYearRepository yearRepository;

    public FiscalYearCloseService(
            LedgerVoucherRepository voucherRepository,
            LedgerAccountRepository accountRepository,
            ChartOfAccountsService chartOfAccountsService,
            ShopFiscalYearRepository yearRepository) {
        this.voucherRepository = voucherRepository;
        this.accountRepository = accountRepository;
        this.chartOfAccountsService = chartOfAccountsService;
        this.yearRepository = yearRepository;
    }

    @Transactional
    public ShopFiscalYear closeYear(ShopFiscalYear year) {
        Long tenantId = year.getTenantId();
        String shopId = year.getShopId();

        var existing = voucherRepository.findDetailedBySource(tenantId, shopId, SOURCE_FY_CLOSE, year.getId());
        if (existing.isPresent()) {
            year.setCloseVoucherId(existing.get().getId());
            year.setOpeningTransferred(Boolean.TRUE);
            year.setStatus("CLOSED");
            if (year.getClosedAt() == null) {
                year.setClosedAt(LocalDateTime.now());
                year.setClosedBy(RequestIdFilter.getCurrentRole());
            }
            return yearRepository.save(year);
        }

        chartOfAccountsService.ensureSystemDefaults();
        Map<Long, LedgerAccount> accounts = new LinkedHashMap<>();
        for (LedgerAccount account : accountRepository.findByTenantIdAndShopIdOrderByCodeAsc(tenantId, shopId)) {
            accounts.put(account.getId(), account);
        }

        Map<Long, double[]> movement = new LinkedHashMap<>();
        List<LedgerVoucher> vouchers = voucherRepository.search(
                tenantId, shopId, year.getStartDate(), year.getEndDate(), "");
        for (LedgerVoucher voucher : vouchers) {
            if (!"POSTED".equalsIgnoreCase(voucher.getStatus())) {
                continue;
            }
            if ("YEAR_END".equalsIgnoreCase(voucher.getVoucherType())
                    || SOURCE_FY_CLOSE.equalsIgnoreCase(voucher.getSourceType())) {
                continue;
            }
            for (LedgerVoucherLine line : voucher.getLines()) {
                double[] totals = movement.computeIfAbsent(line.getAccountId(), id -> new double[] {0, 0});
                totals[0] = round2(totals[0] + safe(line.getDebit()));
                totals[1] = round2(totals[1] + safe(line.getCredit()));
            }
        }

        LedgerAccount retained = accountRepository
                .findByTenantIdAndShopIdAndCode(tenantId, shopId, CODE_RETAINED_EARNINGS)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Missing ledger account code " + CODE_RETAINED_EARNINGS + " — seed defaults first"));

        LedgerVoucher voucher = new LedgerVoucher();
        voucher.setTenantId(tenantId);
        voucher.setShopId(shopId);
        voucher.setVoucherNumber(year.getCode() + "/YE/0001");
        voucher.setVoucherDate(year.getEndDate());
        voucher.setVoucherType("YEAR_END");
        voucher.setStatus("POSTED");
        voucher.setPostedAt(LocalDateTime.now());
        voucher.setSourceType(SOURCE_FY_CLOSE);
        voucher.setSourceId(year.getId());
        voucher.setNarration("Year-end close " + year.getCode() + " — P&L to retained earnings");

        int lineNo = 1;
        double retainedCredit = 0;
        double totalDebit = 0;
        double totalCredit = 0;

        for (Map.Entry<Long, double[]> entry : movement.entrySet()) {
            LedgerAccount account = accounts.get(entry.getKey());
            if (account == null) {
                continue;
            }
            String type = account.getAccountType() == null ? "" : account.getAccountType().trim().toUpperCase(Locale.ROOT);
            double debit = entry.getValue()[0];
            double credit = entry.getValue()[1];
            if ("INCOME".equals(type)) {
                double net = round2(credit - debit);
                if (Math.abs(net) < 0.009) {
                    continue;
                }
                if (net > 0) {
                    voucher.addLine(line(account.getId(), net, 0, "Close income " + account.getCode(), lineNo++));
                    totalDebit = round2(totalDebit + net);
                    retainedCredit = round2(retainedCredit + net);
                } else {
                    double abs = round2(-net);
                    voucher.addLine(line(account.getId(), 0, abs, "Close income " + account.getCode(), lineNo++));
                    totalCredit = round2(totalCredit + abs);
                    retainedCredit = round2(retainedCredit - abs);
                }
            } else if ("EXPENSE".equals(type)) {
                double net = round2(debit - credit);
                if (Math.abs(net) < 0.009) {
                    continue;
                }
                if (net > 0) {
                    voucher.addLine(line(account.getId(), 0, net, "Close expense " + account.getCode(), lineNo++));
                    totalCredit = round2(totalCredit + net);
                    retainedCredit = round2(retainedCredit - net);
                } else {
                    double abs = round2(-net);
                    voucher.addLine(line(account.getId(), abs, 0, "Close expense " + account.getCode(), lineNo++));
                    totalDebit = round2(totalDebit + abs);
                    retainedCredit = round2(retainedCredit + abs);
                }
            }
        }

        if (lineNo == 1) {
            year.setStatus("CLOSED");
            year.setOpeningTransferred(Boolean.TRUE);
            year.setClosedAt(LocalDateTime.now());
            year.setClosedBy(RequestIdFilter.getCurrentRole());
            return yearRepository.save(year);
        }

        if (retainedCredit > 0.009) {
            voucher.addLine(line(retained.getId(), 0, retainedCredit, "Retained earnings " + year.getCode(), lineNo));
            totalCredit = round2(totalCredit + retainedCredit);
        } else if (retainedCredit < -0.009) {
            double loss = round2(-retainedCredit);
            voucher.addLine(line(retained.getId(), loss, 0, "Retained earnings " + year.getCode(), lineNo));
            totalDebit = round2(totalDebit + loss);
        }

        voucher.setTotalDebit(totalDebit);
        voucher.setTotalCredit(totalCredit);
        if (!VoucherService.isBalanced(voucher.getTotalDebit(), voucher.getTotalCredit())) {
            throw new IllegalArgumentException(String.format(
                    Locale.ROOT,
                    "Year-end voucher not balanced: debit %.2f credit %.2f",
                    voucher.getTotalDebit(),
                    voucher.getTotalCredit()));
        }

        LedgerVoucher saved = voucherRepository.save(voucher);
        year.setCloseVoucherId(saved.getId());
        year.setOpeningTransferred(Boolean.TRUE);
        year.setStatus("CLOSED");
        year.setClosedAt(LocalDateTime.now());
        year.setClosedBy(RequestIdFilter.getCurrentRole());
        return yearRepository.save(year);
    }

    private static LedgerVoucherLine line(Long accountId, double debit, double credit, String narration, int lineNo) {
        LedgerVoucherLine line = new LedgerVoucherLine();
        line.setAccountId(accountId);
        line.setDebit(round2(debit));
        line.setCredit(round2(credit));
        line.setLineNarration(narration);
        line.setLineNo(lineNo);
        return line;
    }

    private static double safe(Double value) {
        return value == null ? 0.0 : value;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
