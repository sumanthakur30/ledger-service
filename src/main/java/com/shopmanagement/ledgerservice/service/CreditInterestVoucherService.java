package com.shopmanagement.ledgerservice.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.ledgerservice.dto.CreditInterestVoucherRequest;
import com.shopmanagement.ledgerservice.filter.RequestIdFilter;
import com.shopmanagement.ledgerservice.model.LedgerAccount;
import com.shopmanagement.ledgerservice.model.LedgerVoucher;
import com.shopmanagement.ledgerservice.model.LedgerVoucherLine;
import com.shopmanagement.ledgerservice.repository.LedgerAccountRepository;
import com.shopmanagement.ledgerservice.repository.LedgerVoucherRepository;

/**
 * Dr Debtors (1100); Cr Interest Income (4100).
 */
@Service
public class CreditInterestVoucherService {

    public static final String SOURCE_CREDIT_INTEREST = "CREDIT_INTEREST";
    public static final String CODE_DEBTORS = "1100";
    public static final String CODE_INTEREST_INCOME = "4100";

    private final LedgerVoucherRepository voucherRepository;
    private final LedgerAccountRepository accountRepository;
    private final ChartOfAccountsService chartOfAccountsService;

    public CreditInterestVoucherService(
            LedgerVoucherRepository voucherRepository,
            LedgerAccountRepository accountRepository,
            ChartOfAccountsService chartOfAccountsService) {
        this.voucherRepository = voucherRepository;
        this.accountRepository = accountRepository;
        this.chartOfAccountsService = chartOfAccountsService;
    }

    @Transactional
    public LedgerVoucher postFromCreditInterest(CreditInterestVoucherRequest request) {
        requireManageOrders();
        if (request == null || request.getPostingId() == null) {
            throw new IllegalArgumentException("postingId is required");
        }
        Long tenantId = requireTenantId();
        String shopId = requireShopId();

        Optional<LedgerVoucher> existing = voucherRepository.findDetailedBySource(
                tenantId, shopId, SOURCE_CREDIT_INTEREST, request.getPostingId());
        if (existing.isPresent()) {
            return existing.get();
        }

        double amount = round2(safe(request.getAmount()));
        if (amount <= 0) {
            throw new IllegalArgumentException("Interest amount must be greater than zero");
        }

        chartOfAccountsService.seedDefaults();
        ensureInterestIncome(tenantId, shopId);
        LedgerAccount debtors = requireAccount(tenantId, shopId, CODE_DEBTORS);
        LedgerAccount interest = requireAccount(tenantId, shopId, CODE_INTEREST_INCOME);

        LedgerVoucher voucher = new LedgerVoucher();
        voucher.setTenantId(tenantId);
        voucher.setShopId(shopId);
        voucher.setBranchId(TrialBalanceMath.normalize(request.getBranchId()));
        voucher.setVoucherNumber("CI-" + request.getPostingId());
        voucher.setVoucherDate(request.getAsOfDate() != null ? request.getAsOfDate() : LocalDate.now());
        voucher.setVoucherType("JOURNAL");
        voucher.setStatus("POSTED");
        voucher.setPostedAt(LocalDateTime.now());
        voucher.setSourceType(SOURCE_CREDIT_INTEREST);
        voucher.setSourceId(request.getPostingId());
        String party = request.getCustomerId() != null ? (" customer #" + request.getCustomerId()) : "";
        voucher.setNarration(
                request.getNarration() != null && !request.getNarration().isBlank()
                        ? request.getNarration().trim()
                        : "Overdue credit interest" + party);

        voucher.addLine(line(debtors.getId(), amount, 0, "Interest receivable" + party, 1));
        voucher.addLine(line(interest.getId(), 0, amount, "Interest income" + party, 2));
        voucher.setTotalDebit(amount);
        voucher.setTotalCredit(amount);

        LedgerVoucher saved = voucherRepository.save(voucher);
        return voucherRepository
                .findDetailedByIdAndTenantIdAndShopId(saved.getId(), tenantId, shopId)
                .orElse(saved);
    }

    private void ensureInterestIncome(Long tenantId, String shopId) {
        if (accountRepository.existsByTenantIdAndShopIdAndCode(tenantId, shopId, CODE_INTEREST_INCOME)) {
            return;
        }
        LedgerAccount account = new LedgerAccount();
        account.setTenantId(tenantId);
        account.setShopId(shopId);
        account.setCode(CODE_INTEREST_INCOME);
        account.setName("Interest Income");
        account.setAccountType("INCOME");
        account.setActive(Boolean.TRUE);
        account.setSystemAccount(Boolean.TRUE);
        accountRepository.save(account);
    }

    private LedgerAccount requireAccount(Long tenantId, String shopId, String code) {
        return accountRepository
                .findByTenantIdAndShopIdAndCode(tenantId, shopId, code)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Missing ledger account code " + code + " — seed defaults first"));
    }

    private static LedgerVoucherLine line(Long accountId, double debit, double credit, String narration, int lineNo) {
        LedgerVoucherLine line = new LedgerVoucherLine();
        line.setAccountId(accountId);
        line.setDebit(debit);
        line.setCredit(credit);
        line.setLineNarration(narration);
        line.setLineNo(lineNo);
        return line;
    }

    private void requireManageOrders() {
        // same gate as other voucher services
        String role = RequestIdFilter.getCurrentRole();
        if (role == null) {
            return;
        }
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

    private static double safe(Double v) {
        return v == null || v.isNaN() || v.isInfinite() ? 0 : v;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
