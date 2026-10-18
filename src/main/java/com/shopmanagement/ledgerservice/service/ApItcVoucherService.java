package com.shopmanagement.ledgerservice.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.ledgerservice.dto.ApItcVoucherRequest;
import com.shopmanagement.ledgerservice.filter.RequestIdFilter;
import com.shopmanagement.ledgerservice.model.LedgerAccount;
import com.shopmanagement.ledgerservice.model.LedgerVoucher;
import com.shopmanagement.ledgerservice.model.LedgerVoucherLine;
import com.shopmanagement.ledgerservice.repository.LedgerAccountRepository;
import com.shopmanagement.ledgerservice.repository.LedgerVoucherRepository;

/**
 * Posts Input GST from an approved AP invoice. Separate {@code AP_ITC} source so it
 * never collides with a future full AP voucher or the GRN {@code GOODS_RECEIPT} voucher.
 * Offset is Cr Creditors for tax only — see {@link ApItcPostingMath}.
 */
@Service
public class ApItcVoucherService {

    public static final String SOURCE_AP_ITC = "AP_ITC";
    public static final String CODE_CREDITORS = ApItcPostingMath.CODE_CREDITORS;

    private static final Set<String> ALLOWED_ROLES = Set.of(
            "SUPER_ADMIN", "SHOP_OWNER", "TRADE_ACCOUNTANT", "TRADE_PHARMACIST");
    private static final Set<String> ALLOWED_PERMS = Set.of(
            "MANAGE_ORDERS", "MANAGE_STOCKS", "PROCUREMENT_VIEW", "PROCUREMENT_FINANCE");

    private final LedgerVoucherRepository voucherRepository;
    private final LedgerAccountRepository accountRepository;
    private final ChartOfAccountsService chartOfAccountsService;

    public ApItcVoucherService(
            LedgerVoucherRepository voucherRepository,
            LedgerAccountRepository accountRepository,
            ChartOfAccountsService chartOfAccountsService) {
        this.voucherRepository = voucherRepository;
        this.accountRepository = accountRepository;
        this.chartOfAccountsService = chartOfAccountsService;
    }

    @Transactional
    public LedgerVoucher postFromApInvoice(ApItcVoucherRequest request) {
        requireTradeAccess();
        if (request == null || request.getApInvoiceId() == null) {
            throw new IllegalArgumentException("apInvoiceId is required");
        }
        Long tenantId = requireTenantId();
        String shopId = requireShopId();

        Optional<LedgerVoucher> existing = voucherRepository.findDetailedBySource(
                tenantId, shopId, SOURCE_AP_ITC, request.getApInvoiceId());
        if (existing.isPresent()) {
            return existing.get();
        }

        ApItcPostingMath.ItcVoucher itc = ApItcPostingMath.fromDocument(
                request.getTaxAmount(), request.getCgstAmount(), request.getSgstAmount(), request.getIgstAmount());
        if (itc.totalDebit() <= 0.009) {
            throw new IllegalArgumentException("AP ITC tax amount must be greater than zero");
        }
        if (!ApItcPostingMath.isBalanced(itc.totalDebit(), itc.totalCredit())) {
            throw new IllegalArgumentException(String.format(
                    Locale.ROOT,
                    "AP ITC voucher not balanced: debit %.2f credit %.2f",
                    itc.totalDebit(),
                    itc.totalCredit()));
        }

        chartOfAccountsService.seedDefaults();
        LedgerAccount creditors = requireAccount(tenantId, shopId, CODE_CREDITORS);

        String invNo = request.getInvoiceNumber() != null && !request.getInvoiceNumber().isBlank()
                ? request.getInvoiceNumber().trim()
                : String.valueOf(request.getApInvoiceId());

        LedgerVoucher voucher = new LedgerVoucher();
        voucher.setTenantId(tenantId);
        voucher.setShopId(shopId);
        voucher.setBranchId(TrialBalanceMath.normalize(request.getBranchId()));
        voucher.setVoucherNumber("APITC-" + request.getApInvoiceId());
        voucher.setVoucherDate(request.getInvoiceDate() != null ? request.getInvoiceDate() : LocalDate.now());
        voucher.setVoucherType("JOURNAL");
        voucher.setStatus("POSTED");
        voucher.setPostedAt(LocalDateTime.now());
        voucher.setSourceType(SOURCE_AP_ITC);
        voucher.setSourceId(request.getApInvoiceId());
        voucher.setNarration(
                request.getNarration() != null && !request.getNarration().isBlank()
                        ? request.getNarration().trim()
                        : "AP ITC " + invNo);

        int lineNo = GstLedgerCodes.debitInput(
                voucher, code -> requireAccount(tenantId, shopId, code), itc.split(), invNo, 1);
        voucher.addLine(line(creditors.getId(), 0, itc.creditorsDelta(), "AP tax " + invNo, lineNo));
        voucher.setTotalDebit(itc.totalDebit());
        voucher.setTotalCredit(itc.totalCredit());
        if (!VoucherService.isBalanced(voucher.getTotalDebit(), voucher.getTotalCredit())) {
            throw new IllegalArgumentException(String.format(
                    Locale.ROOT,
                    "AP ITC voucher not balanced: debit %.2f credit %.2f",
                    voucher.getTotalDebit(),
                    voucher.getTotalCredit()));
        }

        LedgerVoucher saved = voucherRepository.save(voucher);
        return voucherRepository
                .findDetailedByIdAndTenantIdAndShopId(saved.getId(), tenantId, shopId)
                .orElse(saved);
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
        line.setDebit(GstSplit.round2(debit));
        line.setCredit(GstSplit.round2(credit));
        line.setLineNarration(narration);
        line.setLineNo(lineNo);
        return line;
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

    private void requireTradeAccess() {
        String role = RequestIdFilter.getCurrentRole();
        if (role != null && ALLOWED_ROLES.contains(role.trim().toUpperCase(Locale.ROOT))) {
            return;
        }
        for (String perm : RequestIdFilter.getCurrentPermissions()) {
            if (perm != null && ALLOWED_PERMS.contains(perm.trim().toUpperCase(Locale.ROOT))) {
                return;
            }
        }
        throw new SecurityException("Forbidden: missing purchase/stock permission");
    }
}
