package com.shopmanagement.ledgerservice.web;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.ledgerservice.dto.AccountBookResponse;
import com.shopmanagement.ledgerservice.dto.AccountsDashboardResponse;
import com.shopmanagement.ledgerservice.dto.CollectionReceiptVoucherRequest;
import com.shopmanagement.ledgerservice.dto.CreateAccountRequest;
import com.shopmanagement.ledgerservice.dto.CreateVoucherRequest;
import com.shopmanagement.ledgerservice.dto.GoodsReceiptVoucherRequest;
import com.shopmanagement.ledgerservice.dto.SalesInvoiceVoucherRequest;
import com.shopmanagement.ledgerservice.dto.TrialBalanceRow;
import com.shopmanagement.ledgerservice.model.LedgerAccount;
import com.shopmanagement.ledgerservice.model.LedgerVoucher;
import com.shopmanagement.ledgerservice.service.AccountBookService;
import com.shopmanagement.ledgerservice.service.AccountsDashboardService;
import com.shopmanagement.ledgerservice.service.ChartOfAccountsService;
import com.shopmanagement.ledgerservice.service.CollectionReceiptVoucherService;
import com.shopmanagement.ledgerservice.service.GoodsReceiptVoucherService;
import com.shopmanagement.ledgerservice.service.SalesInvoiceVoucherService;
import com.shopmanagement.ledgerservice.service.VoucherService;

/**
 * Trade GL (CoA + vouchers). Parallel to clinic RevenueLedger and party AR in order-service.
 */
@RestController
@RequestMapping("/api/v1/ledger")
public class LedgerController {

    private final ChartOfAccountsService chartOfAccountsService;
    private final VoucherService voucherService;
    private final SalesInvoiceVoucherService salesInvoiceVoucherService;
    private final CollectionReceiptVoucherService collectionReceiptVoucherService;
    private final GoodsReceiptVoucherService goodsReceiptVoucherService;
    private final AccountBookService accountBookService;
    private final AccountsDashboardService accountsDashboardService;

    public LedgerController(
            ChartOfAccountsService chartOfAccountsService,
            VoucherService voucherService,
            SalesInvoiceVoucherService salesInvoiceVoucherService,
            CollectionReceiptVoucherService collectionReceiptVoucherService,
            GoodsReceiptVoucherService goodsReceiptVoucherService,
            AccountBookService accountBookService,
            AccountsDashboardService accountsDashboardService) {
        this.chartOfAccountsService = chartOfAccountsService;
        this.voucherService = voucherService;
        this.salesInvoiceVoucherService = salesInvoiceVoucherService;
        this.collectionReceiptVoucherService = collectionReceiptVoucherService;
        this.goodsReceiptVoucherService = goodsReceiptVoucherService;
        this.accountBookService = accountBookService;
        this.accountsDashboardService = accountsDashboardService;
    }

    @GetMapping("/dashboard")
    public AccountsDashboardResponse dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return accountsDashboardService.dashboard(from, to);
    }

    @GetMapping("/accounts")
    public List<LedgerAccount> listAccounts() {
        return chartOfAccountsService.list();
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerAccount createAccount(@RequestBody CreateAccountRequest request) {
        return chartOfAccountsService.create(request);
    }

    @PostMapping("/accounts/seed-defaults")
    public List<LedgerAccount> seedDefaults() {
        return chartOfAccountsService.seedDefaults();
    }

    @GetMapping("/vouchers")
    public List<LedgerVoucher> listVouchers(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String type) {
        return voucherService.list(from, to, type);
    }

    @GetMapping("/vouchers/{id:\\d+}")
    public LedgerVoucher getVoucher(@PathVariable Long id) {
        return voucherService.get(id);
    }

    @PostMapping("/vouchers")
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerVoucher createVoucher(@RequestBody CreateVoucherRequest request) {
        return voucherService.createDraft(request);
    }

    @PostMapping("/vouchers/{id:\\d+}/post")
    public LedgerVoucher postVoucher(@PathVariable Long id) {
        return voucherService.post(id);
    }

    /** Auto-post trade sales invoice → AR/Sales/GST journal (idempotent by invoice id). */
    @PostMapping("/vouchers/from-sales-invoice")
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerVoucher fromSalesInvoice(@RequestBody SalesInvoiceVoucherRequest request) {
        return salesInvoiceVoucherService.postFromSalesInvoice(request);
    }

    /** Auto-post trade collection → Cash/Bank Dr + Debtors Cr (idempotent by payment id). */
    @PostMapping("/vouchers/from-collection")
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerVoucher fromCollection(@RequestBody CollectionReceiptVoucherRequest request) {
        return collectionReceiptVoucherService.postFromCollection(request);
    }

    /** Auto-post trade GRN → Stock Dr + Creditors Cr (idempotent by goods receipt id). */
    @PostMapping("/vouchers/from-goods-receipt")
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerVoucher fromGoodsReceipt(@RequestBody GoodsReceiptVoucherRequest request) {
        return goodsReceiptVoucherService.postFromGoodsReceipt(request);
    }

    @GetMapping("/trial-balance")
    public List<TrialBalanceRow> trialBalance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return voucherService.trialBalance(asOf);
    }

    /** Cash (1000) or Bank (1010) book with opening + running balance. */
    @GetMapping("/cash-book")
    public AccountBookResponse cashBook(
            @RequestParam(defaultValue = "1000") String accountCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return accountBookService.book(accountCode, from, to);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("message", ex.getMessage() != null ? ex.getMessage() : "Bad request"));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> forbidden(SecurityException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", ex.getMessage() != null ? ex.getMessage() : "Forbidden"));
    }
}
