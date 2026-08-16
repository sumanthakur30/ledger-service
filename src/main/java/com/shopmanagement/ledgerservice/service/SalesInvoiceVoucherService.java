package com.shopmanagement.ledgerservice.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.ledgerservice.dto.SalesInvoiceVoucherRequest;
import com.shopmanagement.ledgerservice.filter.RequestIdFilter;
import com.shopmanagement.ledgerservice.model.LedgerAccount;
import com.shopmanagement.ledgerservice.model.LedgerVoucher;
import com.shopmanagement.ledgerservice.model.LedgerVoucherLine;
import com.shopmanagement.ledgerservice.repository.LedgerAccountRepository;
import com.shopmanagement.ledgerservice.repository.LedgerVoucherRepository;

/**
 * Maps trade sales invoices to posted journal vouchers:
 * Dr Debtors (1100) = total; Cr Sales (4000) = net; Cr Output CGST/SGST/IGST (or GST Payable 2100);
 * optional Dr COGS (5300) / Cr Stock (1200) when cogsAmount &gt; 0.
 */
@Service
public class SalesInvoiceVoucherService {

    public static final String SOURCE_SALES_INVOICE = "SALES_INVOICE";
    public static final String CODE_DEBTORS = "1100";
    public static final String CODE_STOCK = "1200";
    public static final String CODE_SALES = "4000";
    public static final String CODE_COGS = "5300";

    private final LedgerVoucherRepository voucherRepository;
    private final LedgerAccountRepository accountRepository;
    private final ChartOfAccountsService chartOfAccountsService;
    private final PeriodLockService periodLockService;

    public SalesInvoiceVoucherService(
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
    public LedgerVoucher postFromSalesInvoice(SalesInvoiceVoucherRequest request) {
        requireManageOrders();
        if (request == null || request.getInvoiceId() == null) {
            throw new IllegalArgumentException("invoiceId is required");
        }
        Long tenantId = requireTenantId();
        String shopId = requireShopId();

        Optional<LedgerVoucher> existing = voucherRepository.findDetailedBySource(
                tenantId, shopId, SOURCE_SALES_INVOICE, request.getInvoiceId());
        if (existing.isPresent()) {
            return existing.get();
        }

        double total = round2(safe(request.getTotalAmount()));
        if (total <= 0) {
            throw new IllegalArgumentException("Invoice total must be greater than zero");
        }
        GstSplit gst = GstSplit.of(
                request.getTaxAmount(), request.getCgstAmount(), request.getSgstAmount(), request.getIgstAmount())
                .cappedTo(total);
        double tax = gst.total;
        double discount = round2(Math.max(0, safe(request.getDiscountAmount())));
        double subtotal = round2(safe(request.getSubtotalAmount()));
        double netSales = round2(subtotal - discount);
        if (netSales < 0) {
            netSales = 0;
        }
        double salesCredit = round2(total - tax);
        double cogs = round2(Math.max(0, safe(request.getCogsAmount())));

        LocalDate voucherDate = request.getInvoiceDate() != null ? request.getInvoiceDate() : LocalDate.now();
        periodLockService.assertOpen(voucherDate);

        chartOfAccountsService.seedDefaults();
        LedgerAccount debtors = requireAccount(tenantId, shopId, CODE_DEBTORS);
        LedgerAccount sales = requireAccount(tenantId, shopId, CODE_SALES);
        LedgerAccount cogsAccount = cogs > 0.009 ? requireAccount(tenantId, shopId, CODE_COGS) : null;
        LedgerAccount stock = cogs > 0.009 ? requireAccount(tenantId, shopId, CODE_STOCK) : null;

        LedgerVoucher voucher = new LedgerVoucher();
        voucher.setTenantId(tenantId);
        voucher.setShopId(shopId);
        voucher.setBranchId(TrialBalanceMath.normalize(request.getBranchId()));
        voucher.setVoucherNumber("SI-" + request.getInvoiceId());
        voucher.setVoucherDate(voucherDate);
        voucher.setVoucherType("SALES");
        voucher.setStatus("POSTED");
        voucher.setPostedAt(LocalDateTime.now());
        voucher.setSourceType(SOURCE_SALES_INVOICE);
        voucher.setSourceId(request.getInvoiceId());
        String invNo = request.getInvoiceNumber() != null ? request.getInvoiceNumber() : String.valueOf(request.getInvoiceId());
        voucher.setNarration(
                request.getNarration() != null && !request.getNarration().isBlank()
                        ? request.getNarration().trim()
                        : "Sales invoice " + invNo);

        int lineNo = 1;
        voucher.addLine(line(debtors.getId(), total, 0, "AR " + invNo, lineNo++));
        if (salesCredit > 0.009) {
            voucher.addLine(line(sales.getId(), 0, salesCredit, "Sales " + invNo, lineNo++));
        }
        lineNo = GstLedgerCodes.creditOutput(voucher, code -> requireAccount(tenantId, shopId, code), gst, invNo, lineNo);
        if (cogs > 0.009 && cogsAccount != null && stock != null) {
            voucher.addLine(line(cogsAccount.getId(), cogs, 0, "COGS " + invNo, lineNo++));
            voucher.addLine(line(stock.getId(), 0, cogs, "Stock issue " + invNo, lineNo++));
        }
        double totalDebit = round2(total + cogs);
        double totalCredit = round2(salesCredit + tax + cogs);
        voucher.setTotalDebit(totalDebit);
        voucher.setTotalCredit(totalCredit);
        if (!VoucherService.isBalanced(voucher.getTotalDebit(), voucher.getTotalCredit())) {
            throw new IllegalArgumentException(String.format(
                    Locale.ROOT,
                    "Sales invoice voucher not balanced: debit %.2f credit %.2f",
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
                .orElseThrow(() -> new IllegalArgumentException("Missing ledger account code " + code + " — seed defaults first"));
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
        if ("SUPER_ADMIN".equals(role) || "SHOP_OWNER".equals(role)) {
            return;
        }
        if (!RequestIdFilter.getCurrentPermissions().contains("MANAGE_ORDERS")) {
            throw new SecurityException("Forbidden: missing permission MANAGE_ORDERS");
        }
    }
}
