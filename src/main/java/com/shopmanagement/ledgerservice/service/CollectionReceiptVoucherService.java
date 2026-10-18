package com.shopmanagement.ledgerservice.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.ledgerservice.dto.CollectionReceiptVoucherRequest;
import com.shopmanagement.ledgerservice.filter.RequestIdFilter;
import com.shopmanagement.ledgerservice.model.LedgerAccount;
import com.shopmanagement.ledgerservice.model.LedgerVoucher;
import com.shopmanagement.ledgerservice.model.LedgerVoucherLine;
import com.shopmanagement.ledgerservice.repository.LedgerAccountRepository;
import com.shopmanagement.ledgerservice.repository.LedgerVoucherRepository;

/**
 * Maps trade AR collections (wholesale SI or later POS/department collection) to posted receipts:
 * Dr Cash (1000) or Bank (1010); Cr Debtors (1100). No GST rewrite of the original sale.
 */
@Service
public class CollectionReceiptVoucherService {

    public static final String SOURCE_COLLECTION = "COLLECTION";
    public static final String SOURCE_POS_COLLECTION = "POS_COLLECTION";
    public static final String CODE_CASH = CollectionReceiptPostingMath.CODE_CASH;
    public static final String CODE_BANK = CollectionReceiptPostingMath.CODE_BANK;
    public static final String CODE_DEBTORS = CollectionReceiptPostingMath.CODE_DEBTORS;

    private static final List<String> POS_POST_PERMISSIONS = List.of(
            "MANAGE_ORDERS", "MANAGE_LAB_ORDERS", "MANAGE_APPOINTMENTS", "DISPENSE_MEDICINES",
            "PROCUREMENT_FINANCE");

    private final LedgerVoucherRepository voucherRepository;
    private final LedgerAccountRepository accountRepository;
    private final ChartOfAccountsService chartOfAccountsService;

    public CollectionReceiptVoucherService(
            LedgerVoucherRepository voucherRepository,
            LedgerAccountRepository accountRepository,
            ChartOfAccountsService chartOfAccountsService) {
        this.voucherRepository = voucherRepository;
        this.accountRepository = accountRepository;
        this.chartOfAccountsService = chartOfAccountsService;
    }

    @Transactional
    public LedgerVoucher postFromCollection(CollectionReceiptVoucherRequest request) {
        return postReceipt(request, SOURCE_COLLECTION, false);
    }

    /** Later collection of a credit POS / OPD / LAB / PHARM bill. Same engine; distinct source_type. */
    @Transactional
    public LedgerVoucher postFromPosCollection(CollectionReceiptVoucherRequest request) {
        return postReceipt(request, SOURCE_POS_COLLECTION, true);
    }

    private LedgerVoucher postReceipt(
            CollectionReceiptVoucherRequest request, String sourceType, boolean posCollection) {
        if (posCollection) {
            requirePosCollectionPermission();
        } else {
            requireManageOrders();
        }
        if (request == null || request.getPaymentId() == null) {
            throw new IllegalArgumentException("paymentId is required");
        }
        Long tenantId = requireTenantId();
        String shopId = requireShopId();

        Optional<LedgerVoucher> existing = voucherRepository.findDetailedBySource(
                tenantId, shopId, sourceType, request.getPaymentId());
        if (existing.isPresent()) {
            return existing.get();
        }

        CollectionReceiptPostingMath.ReceiptSplit split =
                CollectionReceiptPostingMath.split(safe(request.getAmount()), request.getPaymentMethod());
        double amount = split.amount();
        if (amount <= 0) {
            throw new IllegalArgumentException("Collection amount must be greater than zero");
        }

        chartOfAccountsService.seedDefaults();
        String method = request.getPaymentMethod() != null
                ? request.getPaymentMethod().trim().toUpperCase(Locale.ROOT)
                : "CASH";
        LedgerAccount cashOrBank = requireAccount(tenantId, shopId, split.cashBankCode());
        LedgerAccount debtors = requireAccount(tenantId, shopId, CODE_DEBTORS);

        String invNo = documentLabel(request);
        String prefix = posCollection ? "POSRC-" : "RC-";
        String receiptLabel = prefix + request.getPaymentId();

        LedgerVoucher voucher = new LedgerVoucher();
        voucher.setTenantId(tenantId);
        voucher.setShopId(shopId);
        voucher.setBranchId(TrialBalanceMath.normalize(request.getBranchId()));
        voucher.setVoucherNumber(receiptLabel);
        voucher.setVoucherDate(request.getPaymentDate() != null ? request.getPaymentDate() : LocalDate.now());
        voucher.setVoucherType("RECEIPT");
        voucher.setStatus("POSTED");
        voucher.setPostedAt(LocalDateTime.now());
        voucher.setSourceType(sourceType);
        voucher.setSourceId(request.getPaymentId());
        voucher.setNarration(
                request.getNarration() != null && !request.getNarration().isBlank()
                        ? request.getNarration().trim()
                        : (posCollection ? "POS collection receipt " : "Collection receipt ") + receiptLabel
                                + (invNo.isEmpty() ? "" : " for " + invNo)
                                + " (" + method + ")");

        String lineHint = invNo.isEmpty() ? receiptLabel : invNo;
        voucher.addLine(line(cashOrBank.getId(), amount, 0, method + " " + lineHint, 1));
        voucher.addLine(line(debtors.getId(), 0, amount, "AR clear " + lineHint, 2));
        voucher.setTotalDebit(amount);
        voucher.setTotalCredit(amount);
        if (!CollectionReceiptPostingMath.isBalanced(voucher.getTotalDebit(), voucher.getTotalCredit())) {
            throw new IllegalArgumentException(String.format(
                    Locale.ROOT,
                    "Collection receipt voucher not balanced: debit %.2f credit %.2f",
                    voucher.getTotalDebit(),
                    voucher.getTotalCredit()));
        }

        LedgerVoucher saved = voucherRepository.save(voucher);
        return voucherRepository
                .findDetailedByIdAndTenantIdAndShopId(saved.getId(), tenantId, shopId)
                .orElse(saved);
    }

    private static String documentLabel(CollectionReceiptVoucherRequest request) {
        if (request.getOrderNumber() != null && !request.getOrderNumber().isBlank()) {
            return request.getOrderNumber().trim();
        }
        if (request.getInvoiceNumber() != null && !request.getInvoiceNumber().isBlank()) {
            return request.getInvoiceNumber().trim();
        }
        if (request.getOrderId() != null) {
            return String.valueOf(request.getOrderId());
        }
        if (request.getInvoiceId() != null) {
            return String.valueOf(request.getInvoiceId());
        }
        return "";
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

    private void requireManageOrders() {
        String role = RequestIdFilter.getCurrentRole();
        if ("SUPER_ADMIN".equals(role) || "SHOP_OWNER".equals(role) || "TRADE_ACCOUNTANT".equals(role)) {
            return;
        }
        if (!RequestIdFilter.getCurrentPermissions().contains("MANAGE_ORDERS")
                && !RequestIdFilter.getCurrentPermissions().contains("PROCUREMENT_FINANCE")) {
            throw new SecurityException("Forbidden: missing permission MANAGE_ORDERS");
        }
    }

    private void requirePosCollectionPermission() {
        String role = RequestIdFilter.getCurrentRole();
        if ("SUPER_ADMIN".equals(role) || "SHOP_OWNER".equals(role) || "TRADE_ACCOUNTANT".equals(role)) {
            return;
        }
        for (String permission : POS_POST_PERMISSIONS) {
            if (RequestIdFilter.getCurrentPermissions().contains(permission)) {
                return;
            }
        }
        throw new SecurityException("Forbidden: missing permission to post POS collection receipts");
    }
}
