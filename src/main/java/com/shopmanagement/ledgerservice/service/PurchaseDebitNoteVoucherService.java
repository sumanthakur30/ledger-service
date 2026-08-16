package com.shopmanagement.ledgerservice.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.ledgerservice.dto.PurchaseDebitNoteVoucherRequest;
import com.shopmanagement.ledgerservice.filter.RequestIdFilter;
import com.shopmanagement.ledgerservice.model.LedgerAccount;
import com.shopmanagement.ledgerservice.model.LedgerVoucher;
import com.shopmanagement.ledgerservice.model.LedgerVoucherLine;
import com.shopmanagement.ledgerservice.repository.LedgerAccountRepository;
import com.shopmanagement.ledgerservice.repository.LedgerVoucherRepository;

/**
 * Posts a purchase debit note: Dr Creditors (goods + tax), Cr Input GST, and Cr Stock
 * when goods were taken back. Separate {@code PURCHASE_DEBIT_NOTE} source so GRN / AP_ITC
 * / POS vouchers are never rewritten.
 */
@Service
public class PurchaseDebitNoteVoucherService {

    public static final String SOURCE_PURCHASE_DEBIT_NOTE = "PURCHASE_DEBIT_NOTE";
    public static final String CODE_CREDITORS = PurchaseDebitNotePostingMath.CODE_CREDITORS;
    public static final String CODE_STOCK = PurchaseDebitNotePostingMath.CODE_STOCK;

    private static final Set<String> ALLOWED_ROLES = Set.of(
            "SUPER_ADMIN", "SHOP_OWNER", "TRADE_ACCOUNTANT", "TRADE_PHARMACIST");
    private static final Set<String> ALLOWED_PERMS = Set.of(
            "MANAGE_ORDERS", "MANAGE_STOCKS", "PROCUREMENT_VIEW", "PROCUREMENT_FINANCE");

    private final LedgerVoucherRepository voucherRepository;
    private final LedgerAccountRepository accountRepository;
    private final ChartOfAccountsService chartOfAccountsService;

    public PurchaseDebitNoteVoucherService(
            LedgerVoucherRepository voucherRepository,
            LedgerAccountRepository accountRepository,
            ChartOfAccountsService chartOfAccountsService) {
        this.voucherRepository = voucherRepository;
        this.accountRepository = accountRepository;
        this.chartOfAccountsService = chartOfAccountsService;
    }

    @Transactional
    public LedgerVoucher postFromPurchaseReturn(PurchaseDebitNoteVoucherRequest request) {
        requireTradeAccess();
        if (request == null || request.getPurchaseReturnId() == null) {
            throw new IllegalArgumentException("purchaseReturnId is required");
        }
        Long tenantId = requireTenantId();
        String shopId = requireShopId();

        Optional<LedgerVoucher> existing = voucherRepository.findDetailedBySource(
                tenantId, shopId, SOURCE_PURCHASE_DEBIT_NOTE, request.getPurchaseReturnId());
        if (existing.isPresent()) {
            return existing.get();
        }

        boolean creditStock = request.getCreditStock() == null || Boolean.TRUE.equals(request.getCreditStock());
        PurchaseDebitNotePostingMath.DnVoucher dn = PurchaseDebitNotePostingMath.fromDocument(
                request.getStockAmount(),
                request.getTaxAmount(),
                request.getCgstAmount(),
                request.getSgstAmount(),
                request.getIgstAmount(),
                creditStock);
        if (dn.totalDebit() <= 0.009) {
            throw new IllegalArgumentException("Purchase debit note amount must be greater than zero");
        }
        if (!PurchaseDebitNotePostingMath.isBalanced(dn.totalDebit(), dn.totalCredit())) {
            throw new IllegalArgumentException(String.format(
                    Locale.ROOT,
                    "Purchase debit note not balanced: debit %.2f credit %.2f",
                    dn.totalDebit(),
                    dn.totalCredit()));
        }

        chartOfAccountsService.seedDefaults();
        LedgerAccount creditors = requireAccount(tenantId, shopId, CODE_CREDITORS);

        String dnNo = request.getDebitNoteNumber() != null && !request.getDebitNoteNumber().isBlank()
                ? request.getDebitNoteNumber().trim()
                : (request.getReturnNumber() != null && !request.getReturnNumber().isBlank()
                        ? request.getReturnNumber().trim()
                        : String.valueOf(request.getPurchaseReturnId()));

        LedgerVoucher voucher = new LedgerVoucher();
        voucher.setTenantId(tenantId);
        voucher.setShopId(shopId);
        voucher.setBranchId(TrialBalanceMath.normalize(request.getBranchId()));
        voucher.setVoucherNumber("PDN-" + request.getPurchaseReturnId());
        voucher.setVoucherDate(request.getReturnDate() != null ? request.getReturnDate() : LocalDate.now());
        voucher.setVoucherType("DEBIT_NOTE");
        voucher.setStatus("POSTED");
        voucher.setPostedAt(LocalDateTime.now());
        voucher.setSourceType(SOURCE_PURCHASE_DEBIT_NOTE);
        voucher.setSourceId(request.getPurchaseReturnId());
        voucher.setNarration(
                request.getNarration() != null && !request.getNarration().isBlank()
                        ? request.getNarration().trim()
                        : "Purchase debit note " + dnNo);

        int lineNo = 1;
        voucher.addLine(line(creditors.getId(), dn.creditorsDebit(), 0, "AP reverse " + dnNo, lineNo++));
        lineNo = GstLedgerCodes.creditInput(
                voucher, code -> requireAccount(tenantId, shopId, code), dn.split(), dnNo, lineNo);
        if (dn.stockCredit() > 0.009) {
            LedgerAccount stock = requireAccount(tenantId, shopId, CODE_STOCK);
            voucher.addLine(line(stock.getId(), 0, dn.stockCredit(), "Stock return " + dnNo, lineNo));
        }
        voucher.setTotalDebit(dn.totalDebit());
        voucher.setTotalCredit(dn.totalCredit());
        if (!VoucherService.isBalanced(voucher.getTotalDebit(), voucher.getTotalCredit())) {
            throw new IllegalArgumentException(String.format(
                    Locale.ROOT,
                    "Purchase debit note voucher not balanced: debit %.2f credit %.2f",
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
