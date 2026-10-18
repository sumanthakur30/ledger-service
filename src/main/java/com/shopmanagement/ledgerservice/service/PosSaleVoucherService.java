package com.shopmanagement.ledgerservice.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.ledgerservice.dto.PosSaleVoucherRequest;
import com.shopmanagement.ledgerservice.filter.RequestIdFilter;
import com.shopmanagement.ledgerservice.model.LedgerAccount;
import com.shopmanagement.ledgerservice.model.LedgerVoucher;
import com.shopmanagement.ledgerservice.model.LedgerVoucherLine;
import com.shopmanagement.ledgerservice.repository.LedgerAccountRepository;
import com.shopmanagement.ledgerservice.repository.LedgerVoucherRepository;

/**
 * Maps retail POS and clinic department bills (order-service {@code orders}) to posted journals:
 * Dr Cash (1000) and/or Bank (1010) and/or Debtors (1100); Cr Sales (4000); Cr Output CGST/SGST/IGST;
 * optional Dr COGS (5300) / Cr Stock (1200) when cogsAmount &gt; 0.
 * Idempotent by {@code source_type=POS_SALE} + order id. Does not rewrite a posted voucher.
 */
@Service
public class PosSaleVoucherService {

    public static final String SOURCE_POS_SALE = "POS_SALE";
    public static final String CODE_SALES = "4000";
    public static final String CODE_STOCK = "1200";
    public static final String CODE_COGS = "5300";

    private static final List<String> POST_PERMISSIONS = List.of(
            "MANAGE_ORDERS", "MANAGE_LAB_ORDERS", "MANAGE_APPOINTMENTS", "DISPENSE_MEDICINES");

    private final LedgerVoucherRepository voucherRepository;
    private final LedgerAccountRepository accountRepository;
    private final ChartOfAccountsService chartOfAccountsService;
    private final PeriodLockService periodLockService;

    public PosSaleVoucherService(
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
    public LedgerVoucher postFromPosSale(PosSaleVoucherRequest request) {
        requirePostPermission();
        if (request == null || request.getOrderId() == null) {
            throw new IllegalArgumentException("orderId is required");
        }
        Long tenantId = requireTenantId();
        String shopId = requireShopId();

        Optional<LedgerVoucher> existing = voucherRepository.findDetailedBySource(
                tenantId, shopId, SOURCE_POS_SALE, request.getOrderId());
        if (existing.isPresent()) {
            return existing.get();
        }

        if (request.getPaymentStatus() != null
                && "PENDING".equalsIgnoreCase(request.getPaymentStatus().trim())) {
            throw new IllegalArgumentException("Cannot post a PENDING bill to the ledger");
        }

        double total = PosSalePostingMath.round2(safe(request.getTotalAmount()));
        if (total <= 0) {
            throw new IllegalArgumentException("Order total must be greater than zero");
        }
        GstSplit gst = GstSplit.of(
                request.getTaxAmount(), request.getCgstAmount(), request.getSgstAmount(), request.getIgstAmount())
                .cappedTo(total);
        double tax = gst.total;
        double salesCredit = PosSalePostingMath.round2(total - tax);

        PosSalePostingMath.DebitSplit split = PosSalePostingMath.split(
                total, safe(request.getPaidAmount()), request.getPaymentMethod());
        double cogs = PosSalePostingMath.round2(Math.max(0, safe(request.getCogsAmount())));

        LocalDate voucherDate = request.getOrderDate() != null ? request.getOrderDate() : LocalDate.now();
        periodLockService.assertOpen(voucherDate);

        chartOfAccountsService.ensureSystemDefaults();
        LedgerAccount sales = requireAccount(tenantId, shopId, CODE_SALES);
        LedgerAccount cashOrBank = split.cashOrBankDebit() > 0.009
                ? requireAccount(tenantId, shopId, split.cashBankCode())
                : null;
        LedgerAccount debtors = split.debtorsDebit() > 0.009
                ? requireAccount(tenantId, shopId, PosSalePostingMath.CODE_DEBTORS)
                : null;
        LedgerAccount cogsAccount = cogs > 0.009 ? requireAccount(tenantId, shopId, CODE_COGS) : null;
        LedgerAccount stock = cogs > 0.009 ? requireAccount(tenantId, shopId, CODE_STOCK) : null;

        String orderLabel = request.getOrderNumber() != null && !request.getOrderNumber().isBlank()
                ? request.getOrderNumber().trim()
                : String.valueOf(request.getOrderId());
        String billHint = request.getBillType() != null && !request.getBillType().isBlank()
                ? request.getBillType().trim().toUpperCase(Locale.ROOT)
                : "POS";

        LedgerVoucher voucher = new LedgerVoucher();
        voucher.setTenantId(tenantId);
        voucher.setShopId(shopId);
        voucher.setBranchId(TrialBalanceMath.normalize(request.getBranchId()));
        voucher.setVoucherNumber("POS-" + request.getOrderId());
        voucher.setVoucherDate(voucherDate);
        voucher.setVoucherType("SALES");
        voucher.setStatus("POSTED");
        voucher.setPostedAt(LocalDateTime.now());
        voucher.setSourceType(SOURCE_POS_SALE);
        voucher.setSourceId(request.getOrderId());
        voucher.setNarration(
                request.getNarration() != null && !request.getNarration().isBlank()
                        ? request.getNarration().trim()
                        : billHint + " bill " + orderLabel);

        int lineNo = 1;
        if (cashOrBank != null && split.cashOrBankDebit() > 0.009) {
            String method = request.getPaymentMethod() != null ? request.getPaymentMethod().trim() : "CASH";
            voucher.addLine(line(cashOrBank.getId(), split.cashOrBankDebit(), 0,
                    method + " " + orderLabel, lineNo++));
        }
        if (debtors != null && split.debtorsDebit() > 0.009) {
            voucher.addLine(line(debtors.getId(), split.debtorsDebit(), 0, "AR " + orderLabel, lineNo++));
        }
        if (salesCredit > 0.009) {
            voucher.addLine(line(sales.getId(), 0, salesCredit, "Sales " + orderLabel, lineNo++));
        }
        lineNo = GstLedgerCodes.creditOutput(
                voucher, code -> requireAccount(tenantId, shopId, code), gst, orderLabel, lineNo);
        if (cogs > 0.009 && cogsAccount != null && stock != null) {
            voucher.addLine(line(cogsAccount.getId(), cogs, 0, "COGS " + orderLabel, lineNo++));
            voucher.addLine(line(stock.getId(), 0, cogs, "Stock issue " + orderLabel, lineNo++));
        }

        PosSalePostingMath.BalancedTotals totals = PosSalePostingMath.withCogs(split, salesCredit, tax, cogs);
        double totalDebit = totals.debit();
        double totalCredit = totals.credit();
        voucher.setTotalDebit(totalDebit);
        voucher.setTotalCredit(totalCredit);
        if (!VoucherService.isBalanced(voucher.getTotalDebit(), voucher.getTotalCredit())) {
            throw new IllegalArgumentException(String.format(
                    Locale.ROOT,
                    "POS sale voucher not balanced: debit %.2f credit %.2f",
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
        line.setDebit(PosSalePostingMath.round2(debit));
        line.setCredit(PosSalePostingMath.round2(credit));
        line.setLineNarration(narration);
        line.setLineNo(lineNo);
        return line;
    }

    private static double safe(Double value) {
        return value == null ? 0.0 : value;
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

    private void requirePostPermission() {
        String role = RequestIdFilter.getCurrentRole();
        if ("SUPER_ADMIN".equals(role) || "SHOP_OWNER".equals(role)) {
            return;
        }
        for (String permission : POST_PERMISSIONS) {
            if (RequestIdFilter.getCurrentPermissions().contains(permission)) {
                return;
            }
        }
        throw new SecurityException("Forbidden: missing permission to post POS/department bills");
    }
}
