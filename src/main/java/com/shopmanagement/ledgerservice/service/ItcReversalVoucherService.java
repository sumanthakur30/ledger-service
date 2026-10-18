package com.shopmanagement.ledgerservice.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.ledgerservice.dto.ItcReversalVoucherRequest;
import com.shopmanagement.ledgerservice.filter.RequestIdFilter;
import com.shopmanagement.ledgerservice.model.LedgerAccount;
import com.shopmanagement.ledgerservice.model.LedgerVoucher;
import com.shopmanagement.ledgerservice.model.LedgerVoucherLine;
import com.shopmanagement.ledgerservice.repository.LedgerAccountRepository;
import com.shopmanagement.ledgerservice.repository.LedgerVoucherRepository;

/**
 * Free-sample expense and section 17(5)(h) reversal.
 * Idempotent on source type plus the free-issue line id. A return uses a separate source type.
 */
@Service
public class ItcReversalVoucherService {

    public static final String SOURCE_ITC_REVERSAL = "ITC_REVERSAL";
    public static final String SOURCE_ITC_REVERSAL_RETURN = "ITC_REVERSAL_RETURN";

    private final LedgerVoucherRepository voucherRepository;
    private final LedgerAccountRepository accountRepository;
    private final ChartOfAccountsService chartOfAccountsService;
    private final PeriodLockService periodLockService;

    public ItcReversalVoucherService(
            LedgerVoucherRepository voucherRepository,
            LedgerAccountRepository accountRepository,
            ChartOfAccountsService chartOfAccountsService,
            PeriodLockService periodLockService) {
        this.voucherRepository = voucherRepository;
        this.accountRepository = accountRepository;
        this.chartOfAccountsService = chartOfAccountsService;
        this.periodLockService = periodLockService;
    }

    @Transactional
    public LedgerVoucher post(ItcReversalVoucherRequest request) {
        requireAccess();
        if (request == null || request.getSourceId() == null) {
            throw new IllegalArgumentException("sourceId is required");
        }
        Long tenantId = requireTenantId();
        String shopId = requireShopId();
        String source = request.isCompensating() ? SOURCE_ITC_REVERSAL_RETURN : SOURCE_ITC_REVERSAL;
        Optional<LedgerVoucher> existing = voucherRepository.findDetailedBySource(tenantId, shopId, source, request.getSourceId());
        if (existing.isPresent()) {
            return existing.get();
        }
        ItcReversalPostingMath.Posting posting = ItcReversalPostingMath.of(
                request.getInventoryCost(), request.getCgstAmount(), request.getSgstAmount(),
                request.getIgstAmount(), request.getCessAmount());
        if (posting.total() <= 0.009) {
            throw new IllegalArgumentException("Promotion cost or traced tax is required");
        }
        if (!ItcReversalPostingMath.balanced(posting)) {
            throw new IllegalArgumentException("ITC reversal voucher is not balanced");
        }
        LocalDate voucherDate = request.getVoucherDate() != null ? request.getVoucherDate() : LocalDate.now();
        periodLockService.assertOpen(voucherDate);
        chartOfAccountsService.ensureSystemDefaults();
        LedgerAccount expense = requireAccount(tenantId, shopId, ItcReversalPostingMath.PROMOTION_EXPENSE);
        LedgerAccount stock = requireAccount(tenantId, shopId, ItcReversalPostingMath.STOCK);
        String doc = request.getDocumentNumber() == null || request.getDocumentNumber().isBlank()
                ? "PROMO-" + request.getSourceId()
                : request.getDocumentNumber().trim();
        GstSplit split = GstSplit.of(posting.tax(), posting.cgst(), posting.sgst(), posting.igst());

        LedgerVoucher voucher = new LedgerVoucher();
        voucher.setTenantId(tenantId);
        voucher.setShopId(shopId);
        voucher.setBranchId(TrialBalanceMath.normalize(request.getBranchId()));
        voucher.setVoucherNumber((request.isCompensating() ? "ITCR-" : "ITC-") + request.getSourceId());
        voucher.setVoucherDate(voucherDate);
        voucher.setVoucherType("JOURNAL");
        voucher.setStatus("POSTED");
        voucher.setPostedAt(LocalDateTime.now());
        voucher.setSourceType(source);
        voucher.setSourceId(request.getSourceId());
        voucher.setNarration(request.getNarration() == null || request.getNarration().isBlank()
                ? (request.isCompensating() ? "Reverse promotion " : "Promotion / ITC reversal ") + doc
                : request.getNarration().trim());

        int lineNo = 1;
        if (request.isCompensating()) {
            if (posting.cost() > 0) {
                voucher.addLine(line(stock.getId(), posting.cost(), 0, "Stock restored " + doc, lineNo++));
            }
            lineNo = GstLedgerCodes.debitInput(voucher, code -> requireAccount(tenantId, shopId, code), split, doc, lineNo);
            voucher.addLine(line(expense.getId(), 0, posting.total(), "Reverse promotion " + doc, lineNo));
        } else {
            if (posting.cost() > 0) {
                voucher.addLine(line(expense.getId(), posting.cost(), 0, "Promotion cost " + doc, lineNo++));
                voucher.addLine(line(stock.getId(), 0, posting.cost(), "Stock issued " + doc, lineNo++));
            }
            if (posting.tax() > 0) {
                voucher.addLine(line(expense.getId(), posting.tax(), 0, "Blocked ITC " + doc, lineNo++));
                GstLedgerCodes.creditInput(voucher, code -> requireAccount(tenantId, shopId, code), split, doc, lineNo);
            }
        }
        voucher.setTotalDebit(posting.total());
        voucher.setTotalCredit(posting.total());
        if (!VoucherService.isBalanced(voucher.getTotalDebit(), voucher.getTotalCredit())) {
            throw new IllegalArgumentException(String.format(
                    Locale.ROOT, "ITC reversal voucher not balanced: debit %.2f credit %.2f",
                    voucher.getTotalDebit(), voucher.getTotalCredit()));
        }
        LedgerVoucher saved = voucherRepository.save(voucher);
        return voucherRepository.findDetailedByIdAndTenantIdAndShopId(saved.getId(), tenantId, shopId).orElse(saved);
    }

    private LedgerAccount requireAccount(Long tenantId, String shopId, String code) {
        return accountRepository.findByTenantIdAndShopIdAndCode(tenantId, shopId, code)
                .orElseThrow(() -> new IllegalArgumentException("Missing ledger account code " + code));
    }

    private static LedgerVoucherLine line(Long accountId, double debit, double credit, String narration, int lineNo) {
        LedgerVoucherLine row = new LedgerVoucherLine();
        row.setAccountId(accountId);
        row.setDebit(Math.round(debit * 100.0) / 100.0);
        row.setCredit(Math.round(credit * 100.0) / 100.0);
        row.setLineNarration(narration);
        row.setLineNo(lineNo);
        return row;
    }

    private void requireAccess() {
        String role = RequestIdFilter.getCurrentRole();
        if ("SUPER_ADMIN".equals(role) || "SHOP_OWNER".equals(role)) {
            return;
        }
        if (!RequestIdFilter.getCurrentPermissions().contains("MANAGE_ORDERS")
                && !RequestIdFilter.getCurrentPermissions().contains("MANAGE_GST")
                && !RequestIdFilter.getCurrentPermissions().contains("PROCUREMENT_FINANCE")) {
            throw new SecurityException("Forbidden: missing permission to post ITC reversal");
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
}
