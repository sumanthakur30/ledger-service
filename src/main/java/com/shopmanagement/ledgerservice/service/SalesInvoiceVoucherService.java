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
 * Dr Debtors (1100) = total; Cr Sales (4000) = net; Cr GST Payable (2100) = tax.
 */
@Service
public class SalesInvoiceVoucherService {

    public static final String SOURCE_SALES_INVOICE = "SALES_INVOICE";
    public static final String CODE_DEBTORS = "1100";
    public static final String CODE_SALES = "4000";
    public static final String CODE_GST_PAYABLE = "2100";

    private final LedgerVoucherRepository voucherRepository;
    private final LedgerAccountRepository accountRepository;
    private final ChartOfAccountsService chartOfAccountsService;

    public SalesInvoiceVoucherService(
            LedgerVoucherRepository voucherRepository,
            LedgerAccountRepository accountRepository,
            ChartOfAccountsService chartOfAccountsService) {
        this.voucherRepository = voucherRepository;
        this.accountRepository = accountRepository;
        this.chartOfAccountsService = chartOfAccountsService;
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
        double tax = round2(Math.max(0, safe(request.getTaxAmount())));
        double discount = round2(Math.max(0, safe(request.getDiscountAmount())));
        double subtotal = round2(safe(request.getSubtotalAmount()));
        double netSales = round2(subtotal - discount);
        if (netSales < 0) {
            netSales = 0;
        }
        // Keep voucher balanced if tax/net don't add to total (rounding / inclusive pricing).
        double salesCredit = round2(total - tax);
        if (salesCredit < 0) {
            salesCredit = 0;
            tax = total;
        }

        chartOfAccountsService.seedDefaults();
        LedgerAccount debtors = requireAccount(tenantId, shopId, CODE_DEBTORS);
        LedgerAccount sales = requireAccount(tenantId, shopId, CODE_SALES);
        LedgerAccount gst = requireAccount(tenantId, shopId, CODE_GST_PAYABLE);

        LedgerVoucher voucher = new LedgerVoucher();
        voucher.setTenantId(tenantId);
        voucher.setShopId(shopId);
        voucher.setVoucherNumber("SI-" + request.getInvoiceId());
        voucher.setVoucherDate(request.getInvoiceDate() != null ? request.getInvoiceDate() : LocalDate.now());
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
        if (tax > 0.009) {
            voucher.addLine(line(gst.getId(), 0, tax, "GST " + invNo, lineNo++));
        }
        voucher.setTotalDebit(total);
        voucher.setTotalCredit(round2(salesCredit + tax));
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
