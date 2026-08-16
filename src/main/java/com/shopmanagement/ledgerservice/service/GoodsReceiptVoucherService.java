package com.shopmanagement.ledgerservice.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.ledgerservice.dto.GoodsReceiptVoucherRequest;
import com.shopmanagement.ledgerservice.filter.RequestIdFilter;
import com.shopmanagement.ledgerservice.model.LedgerAccount;
import com.shopmanagement.ledgerservice.model.LedgerVoucher;
import com.shopmanagement.ledgerservice.model.LedgerVoucherLine;
import com.shopmanagement.ledgerservice.repository.LedgerAccountRepository;
import com.shopmanagement.ledgerservice.repository.LedgerVoucherRepository;

/**
 * Maps posted trade GRNs to purchase vouchers:
 * Dr Stock / Inventory (1200) = landed stock value; Cr Sundry Creditors (2000).
 * Freight is capitalized into stock (matches LandedCostAllocator) — no separate 5100 line.
 */
@Service
public class GoodsReceiptVoucherService {

    public static final String SOURCE_GOODS_RECEIPT = "GOODS_RECEIPT";
    public static final String CODE_STOCK = "1200";
    public static final String CODE_CREDITORS = "2000";

    private static final Set<String> ALLOWED_ROLES = Set.of(
            "SUPER_ADMIN", "SHOP_OWNER", "TRADE_ACCOUNTANT", "TRADE_PHARMACIST");
    private static final Set<String> ALLOWED_PERMS = Set.of(
            "MANAGE_ORDERS", "MANAGE_STOCKS", "PROCUREMENT_VIEW", "PROCUREMENT_FINANCE");

    private final LedgerVoucherRepository voucherRepository;
    private final LedgerAccountRepository accountRepository;
    private final ChartOfAccountsService chartOfAccountsService;

    public GoodsReceiptVoucherService(
            LedgerVoucherRepository voucherRepository,
            LedgerAccountRepository accountRepository,
            ChartOfAccountsService chartOfAccountsService) {
        this.voucherRepository = voucherRepository;
        this.accountRepository = accountRepository;
        this.chartOfAccountsService = chartOfAccountsService;
    }

    @Transactional
    public LedgerVoucher postFromGoodsReceipt(GoodsReceiptVoucherRequest request) {
        requireTradeAccess();
        if (request == null || request.getGoodsReceiptId() == null) {
            throw new IllegalArgumentException("goodsReceiptId is required");
        }
        Long tenantId = requireTenantId();
        String shopId = requireShopId();

        Optional<LedgerVoucher> existing = voucherRepository.findDetailedBySource(
                tenantId, shopId, SOURCE_GOODS_RECEIPT, request.getGoodsReceiptId());
        if (existing.isPresent()) {
            return existing.get();
        }

        double stock = round2(safe(request.getStockAmount()));
        double creditors = round2(safe(request.getCreditorsAmount()) > 0
                ? safe(request.getCreditorsAmount())
                : stock);
        if (stock <= 0 || creditors <= 0) {
            throw new IllegalArgumentException("GRN stock/creditors amount must be greater than zero");
        }
        // Prefer balancing on creditors when tiny rounding drift exists.
        if (Math.abs(stock - creditors) > 0.009 && Math.abs(stock - creditors) <= 0.05) {
            stock = creditors;
        }
        if (Math.abs(stock - creditors) > 0.009) {
            throw new IllegalArgumentException(String.format(
                    Locale.ROOT,
                    "GRN voucher not balanced: stock %.2f creditors %.2f",
                    stock,
                    creditors));
        }

        chartOfAccountsService.seedDefaults();
        LedgerAccount inventory = requireAccount(tenantId, shopId, CODE_STOCK);
        LedgerAccount ap = requireAccount(tenantId, shopId, CODE_CREDITORS);

        String grnNo = request.getGrnNumber() != null && !request.getGrnNumber().isBlank()
                ? request.getGrnNumber().trim()
                : String.valueOf(request.getGoodsReceiptId());

        LedgerVoucher voucher = new LedgerVoucher();
        voucher.setTenantId(tenantId);
        voucher.setShopId(shopId);
        voucher.setBranchId(TrialBalanceMath.normalize(request.getBranchId()));
        voucher.setVoucherNumber("GRN-" + request.getGoodsReceiptId());
        voucher.setVoucherDate(request.getReceiptDate() != null ? request.getReceiptDate() : LocalDate.now());
        voucher.setVoucherType("PURCHASE");
        voucher.setStatus("POSTED");
        voucher.setPostedAt(LocalDateTime.now());
        voucher.setSourceType(SOURCE_GOODS_RECEIPT);
        voucher.setSourceId(request.getGoodsReceiptId());
        double freight = round2(Math.max(0, safe(request.getFreightAmount())));
        String freightNote = freight > 0.009 ? String.format(Locale.ROOT, " incl. freight %.2f", freight) : "";
        voucher.setNarration(
                request.getNarration() != null && !request.getNarration().isBlank()
                        ? request.getNarration().trim()
                        : "Goods receipt " + grnNo + freightNote);

        voucher.addLine(line(inventory.getId(), stock, 0, "Stock " + grnNo, 1));
        voucher.addLine(line(ap.getId(), 0, creditors, "AP " + grnNo, 2));
        voucher.setTotalDebit(stock);
        voucher.setTotalCredit(creditors);
        if (!VoucherService.isBalanced(voucher.getTotalDebit(), voucher.getTotalCredit())) {
            throw new IllegalArgumentException(String.format(
                    Locale.ROOT,
                    "GRN voucher not balanced: debit %.2f credit %.2f",
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
