package com.shopmanagement.ledgerservice.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.ledgerservice.dto.CreateVoucherRequest;
import com.shopmanagement.ledgerservice.dto.CreateVoucherRequest.VoucherLineRequest;
import com.shopmanagement.ledgerservice.dto.TrialBalanceRow;
import com.shopmanagement.ledgerservice.filter.RequestIdFilter;
import com.shopmanagement.ledgerservice.model.LedgerAccount;
import com.shopmanagement.ledgerservice.model.LedgerVoucher;
import com.shopmanagement.ledgerservice.model.LedgerVoucherLine;
import com.shopmanagement.ledgerservice.repository.LedgerAccountRepository;
import com.shopmanagement.ledgerservice.repository.LedgerVoucherRepository;

@Service
public class VoucherService {

    private final LedgerVoucherRepository voucherRepository;
    private final LedgerAccountRepository accountRepository;
    private final PeriodLockService periodLockService;
    private final FiscalYearService fiscalYearService;

    public VoucherService(
            LedgerVoucherRepository voucherRepository,
            LedgerAccountRepository accountRepository,
            PeriodLockService periodLockService,
            FiscalYearService fiscalYearService) {
        this.voucherRepository = voucherRepository;
        this.accountRepository = accountRepository;
        this.periodLockService = periodLockService;
        this.fiscalYearService = fiscalYearService;
    }

    /** Hardened list — default size 200, max 500 (matches wholesale SO/challan). */
    @Transactional(readOnly = true)
    public List<LedgerVoucher> list(LocalDate from, LocalDate to, String voucherType, Integer size) {
        requireManageOrders();
        String type = blank(voucherType) ? "" : voucherType.trim().toUpperCase(Locale.ROOT);
        LocalDate fromDate = from != null ? from : LocalDate.of(2000, 1, 1);
        LocalDate toDate = to != null ? to : LocalDate.of(2100, 12, 31);
        int limit = size == null ? 200 : Math.max(1, Math.min(500, size));
        List<LedgerVoucher> vouchers = voucherRepository.searchLimited(
                requireTenantId(), requireShopId(), fromDate, toDate, type, Pageable.ofSize(limit));
        // Force-init lines while session is open (defense in depth with EntityGraph).
        vouchers.forEach(v -> v.getLines().size());
        return vouchers;
    }

    @Transactional(readOnly = true)
    public LedgerVoucher get(Long id) {
        requireManageOrders();
        return voucherRepository
                .findDetailedByIdAndTenantIdAndShopId(id, requireTenantId(), requireShopId())
                .orElseThrow(() -> new IllegalArgumentException("Voucher not found: " + id));
    }

    @Transactional
    public LedgerVoucher createDraft(CreateVoucherRequest request) {
        requireManageOrders();
        if (request == null || request.getLines() == null || request.getLines().size() < 2) {
            throw new IllegalArgumentException("At least two voucher lines are required");
        }
        Long tenantId = requireTenantId();
        String shopId = requireShopId();

        LedgerVoucher voucher = new LedgerVoucher();
        voucher.setTenantId(tenantId);
        voucher.setShopId(shopId);
        voucher.setBranchId(TrialBalanceMath.normalize(request.getBranchId()));
        voucher.setVoucherDate(request.getVoucherDate() != null ? request.getVoucherDate() : LocalDate.now());
        voucher.setVoucherType(
                blank(request.getVoucherType()) ? "JOURNAL" : request.getVoucherType().trim().toUpperCase(Locale.ROOT));
        periodLockService.assertOpen(voucher.getVoucherDate());
        voucher.setVoucherNumber(fiscalYearService.nextVoucherNumber(
                voucher.getVoucherType(), voucher.getVoucherDate()));
        voucher.setStatus("DRAFT");
        voucher.setNarration(trimToNull(request.getNarration()));
        voucher.setSourceType(trimToNull(request.getSourceType()));
        voucher.setSourceId(request.getSourceId());

        double debitTotal = 0;
        double creditTotal = 0;
        int lineNo = 1;
        for (VoucherLineRequest lineReq : request.getLines()) {
            if (lineReq == null || lineReq.getAccountId() == null) {
                throw new IllegalArgumentException("Each line requires accountId");
            }
            accountRepository
                    .findByIdAndTenantIdAndShopId(lineReq.getAccountId(), tenantId, shopId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown account: " + lineReq.getAccountId()));
            double debit = safe(lineReq.getDebit());
            double credit = safe(lineReq.getCredit());
            if (debit < 0 || credit < 0) {
                throw new IllegalArgumentException("Debit/credit cannot be negative");
            }
            if (debit > 0 && credit > 0) {
                throw new IllegalArgumentException("A line cannot have both debit and credit");
            }
            if (debit <= 0 && credit <= 0) {
                throw new IllegalArgumentException("A line must have debit or credit");
            }
            LedgerVoucherLine line = new LedgerVoucherLine();
            line.setAccountId(lineReq.getAccountId());
            line.setDebit(round2(debit));
            line.setCredit(round2(credit));
            line.setLineNarration(trimToNull(lineReq.getLineNarration()));
            line.setLineNo(lineNo++);
            voucher.addLine(line);
            debitTotal += line.getDebit();
            creditTotal += line.getCredit();
        }
        debitTotal = round2(debitTotal);
        creditTotal = round2(creditTotal);
        if (Math.abs(debitTotal - creditTotal) > 0.009) {
            throw new IllegalArgumentException(
                    String.format(Locale.ROOT, "Voucher not balanced: debit %.2f != credit %.2f", debitTotal, creditTotal));
        }
        voucher.setTotalDebit(debitTotal);
        voucher.setTotalCredit(creditTotal);
        return voucherRepository.save(voucher);
    }

    @Transactional
    public LedgerVoucher post(Long id) {
        requireManageOrders();
        LedgerVoucher voucher = get(id);
        if (!"DRAFT".equalsIgnoreCase(voucher.getStatus())) {
            throw new IllegalArgumentException("Only DRAFT vouchers can be posted");
        }
        if (Math.abs(safe(voucher.getTotalDebit()) - safe(voucher.getTotalCredit())) > 0.009) {
            throw new IllegalArgumentException("Cannot post unbalanced voucher");
        }
        periodLockService.assertOpen(voucher.getVoucherDate());
        voucher.setStatus("POSTED");
        voucher.setPostedAt(LocalDateTime.now());
        return voucherRepository.save(voucher);
    }

    @Transactional(readOnly = true)
    public List<TrialBalanceRow> trialBalance(LocalDate asOf) {
        return trialBalance(asOf, null);
    }

    @Transactional(readOnly = true)
    public List<TrialBalanceRow> trialBalance(LocalDate asOf, Long branchId) {
        LocalDate cutoff = asOf != null ? asOf : LocalDate.now();
        return accumulatePosted(LocalDate.of(2000, 1, 1), cutoff, false, branchId);
    }

    /** Posted voucher debit/credit totals for a closed date range (inclusive). Excludes year-end close. */
    @Transactional(readOnly = true)
    public List<TrialBalanceRow> periodMovement(LocalDate from, LocalDate to) {
        return periodMovement(from, to, null);
    }

    @Transactional(readOnly = true)
    public List<TrialBalanceRow> periodMovement(LocalDate from, LocalDate to, Long branchId) {
        LocalDate fromDate = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate toDate = to != null ? to : LocalDate.now();
        return accumulatePosted(fromDate, toDate, true, branchId);
    }

    private List<TrialBalanceRow> accumulatePosted(
            LocalDate fromDate, LocalDate toDate, boolean excludeYearEnd, Long branchId) {
        requireManageOrders();
        Long tenantId = requireTenantId();
        String shopId = requireShopId();
        List<LedgerAccount> accounts = accountRepository.findByTenantIdAndShopIdOrderByCodeAsc(tenantId, shopId);
        List<TrialBalanceMath.AccountSeed> seeds = new ArrayList<>();
        for (LedgerAccount account : accounts) {
            seeds.add(new TrialBalanceMath.AccountSeed(
                    account.getId(), account.getCode(), account.getName(), account.getAccountType()));
        }
        List<LedgerVoucher> vouchers = voucherRepository.search(tenantId, shopId, fromDate, toDate, "");
        List<TrialBalanceMath.PostedVoucher> posted = new ArrayList<>();
        for (LedgerVoucher voucher : vouchers) {
            List<TrialBalanceMath.Line> lines = new ArrayList<>();
            for (LedgerVoucherLine line : voucher.getLines()) {
                lines.add(new TrialBalanceMath.Line(
                        line.getAccountId(), safe(line.getDebit()), safe(line.getCredit())));
            }
            posted.add(new TrialBalanceMath.PostedVoucher(
                    voucher.getBranchId(),
                    voucher.getStatus(),
                    voucher.getVoucherType(),
                    voucher.getSourceType(),
                    lines));
        }
        return TrialBalanceMath.accumulate(
                seeds, posted, TrialBalanceMath.normalize(branchId), excludeYearEnd);
    }

    /** Pure helper for unit tests — validates line totals balance. */
    public static boolean isBalanced(double debitTotal, double creditTotal) {
        return Math.abs(round2(debitTotal) - round2(creditTotal)) <= 0.009;
    }

    private static double safe(Double value) {
        return value == null ? 0.0 : value;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
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
