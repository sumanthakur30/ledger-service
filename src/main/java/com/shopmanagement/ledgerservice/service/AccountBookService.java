package com.shopmanagement.ledgerservice.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.ledgerservice.dto.AccountBookResponse;
import com.shopmanagement.ledgerservice.dto.AccountBookResponse.AccountBookLine;
import com.shopmanagement.ledgerservice.filter.RequestIdFilter;
import com.shopmanagement.ledgerservice.model.LedgerAccount;
import com.shopmanagement.ledgerservice.model.LedgerVoucher;
import com.shopmanagement.ledgerservice.model.LedgerVoucherLine;
import com.shopmanagement.ledgerservice.repository.LedgerAccountRepository;
import com.shopmanagement.ledgerservice.repository.LedgerVoucherRepository;

/**
 * Cash (1000) / Bank (1010) books from posted voucher lines with running balance.
 */
@Service
public class AccountBookService {

    private static final Set<String> BOOK_CODES = Set.of("1000", "1010");

    private final LedgerVoucherRepository voucherRepository;
    private final LedgerAccountRepository accountRepository;

    public AccountBookService(
            LedgerVoucherRepository voucherRepository,
            LedgerAccountRepository accountRepository) {
        this.voucherRepository = voucherRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public AccountBookResponse book(String accountCode, LocalDate from, LocalDate to) {
        requireAccess();
        String code = accountCode == null ? "" : accountCode.trim();
        if (!BOOK_CODES.contains(code)) {
            throw new IllegalArgumentException("accountCode must be 1000 (Cash) or 1010 (Bank)");
        }
        Long tenantId = requireTenantId();
        String shopId = requireShopId();
        LocalDate fromDate = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate toDate = to != null ? to : LocalDate.now();
        if (toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("to must be on or after from");
        }

        LedgerAccount account = accountRepository
                .findByTenantIdAndShopIdAndCode(tenantId, shopId, code)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Account " + code + " not found — seed chart of accounts defaults first"));

        // Opening: all posted activity before fromDate
        LocalDate epoch = LocalDate.of(2000, 1, 1);
        LocalDate dayBefore = fromDate.minusDays(1);
        double opening = 0;
        if (!dayBefore.isBefore(epoch)) {
            List<LedgerVoucher> prior = voucherRepository.search(tenantId, shopId, epoch, dayBefore, "");
            opening = sumNetForAccount(prior, account.getId());
        }

        List<LedgerVoucher> period = voucherRepository.search(tenantId, shopId, fromDate, toDate, "");
        period.sort(Comparator
                .comparing(LedgerVoucher::getVoucherDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(LedgerVoucher::getId, Comparator.nullsLast(Comparator.naturalOrder())));

        List<AccountBookLine> lines = new ArrayList<>();
        double running = opening;
        double periodDebit = 0;
        double periodCredit = 0;
        for (LedgerVoucher voucher : period) {
            if (!"POSTED".equalsIgnoreCase(voucher.getStatus()) || voucher.getLines() == null) {
                continue;
            }
            for (LedgerVoucherLine line : voucher.getLines()) {
                if (line.getAccountId() == null || !line.getAccountId().equals(account.getId())) {
                    continue;
                }
                double debit = round2(safe(line.getDebit()));
                double credit = round2(safe(line.getCredit()));
                if (debit <= 0.009 && credit <= 0.009) {
                    continue;
                }
                running = round2(running + debit - credit);
                periodDebit = round2(periodDebit + debit);
                periodCredit = round2(periodCredit + credit);
                String narr = line.getLineNarration();
                if (narr == null || narr.isBlank()) {
                    narr = voucher.getNarration();
                }
                lines.add(new AccountBookLine(
                        line.getId(),
                        voucher.getId(),
                        voucher.getVoucherNumber(),
                        voucher.getVoucherDate(),
                        voucher.getVoucherType(),
                        narr,
                        voucher.getSourceType(),
                        voucher.getSourceId(),
                        debit,
                        credit,
                        running,
                        Boolean.TRUE.equals(line.getReconciled())));
            }
        }

        return new AccountBookResponse(
                account.getCode(),
                account.getName(),
                account.getId(),
                fromDate,
                toDate,
                round2(opening),
                periodDebit,
                periodCredit,
                round2(running),
                lines);
    }

    private static double sumNetForAccount(List<LedgerVoucher> vouchers, Long accountId) {
        double net = 0;
        for (LedgerVoucher voucher : vouchers) {
            if (!"POSTED".equalsIgnoreCase(voucher.getStatus()) || voucher.getLines() == null) {
                continue;
            }
            for (LedgerVoucherLine line : voucher.getLines()) {
                if (line.getAccountId() != null && line.getAccountId().equals(accountId)) {
                    net += safe(line.getDebit()) - safe(line.getCredit());
                }
            }
        }
        return round2(net);
    }

    private static double safe(Double value) {
        return value == null ? 0.0 : value;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private Long requireTenantId() {
        Long tenantId = RequestIdFilter.getCurrentTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Missing tenant context");
        }
        return tenantId;
    }

    private String requireShopId() {
        String shopId = RequestIdFilter.getCurrentShopId();
        if (shopId == null || shopId.isBlank()) {
            throw new IllegalStateException("Missing shop context");
        }
        return shopId;
    }

    private void requireAccess() {
        String role = RequestIdFilter.getCurrentRole();
        if (role != null) {
            String r = role.trim().toUpperCase(Locale.ROOT);
            if ("SUPER_ADMIN".equals(r) || "SHOP_OWNER".equals(r) || "TRADE_ACCOUNTANT".equals(r)) {
                return;
            }
        }
        var perms = RequestIdFilter.getCurrentPermissions();
        if (perms.contains("MANAGE_ORDERS") || perms.contains("PROCUREMENT_FINANCE")
                || perms.contains("MANAGE_FINANCE")) {
            return;
        }
        throw new SecurityException("Forbidden: missing permission MANAGE_ORDERS");
    }
}
