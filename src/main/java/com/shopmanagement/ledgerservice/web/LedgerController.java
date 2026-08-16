package com.shopmanagement.ledgerservice.web;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.ledgerservice.dto.AccountBookResponse;
import com.shopmanagement.ledgerservice.dto.ApItcVoucherRequest;
import com.shopmanagement.ledgerservice.dto.AccountsDashboardResponse;
import com.shopmanagement.ledgerservice.dto.BalanceSheetResponse;
import com.shopmanagement.ledgerservice.dto.CashVsProfitResponse;
import com.shopmanagement.ledgerservice.dto.CollectionReceiptVoucherRequest;
import com.shopmanagement.ledgerservice.dto.CreateAccountRequest;
import com.shopmanagement.ledgerservice.dto.CreateVoucherRequest;
import com.shopmanagement.ledgerservice.dto.CreditInterestVoucherRequest;
import com.shopmanagement.ledgerservice.dto.GoodsReceiptVoucherRequest;
import com.shopmanagement.ledgerservice.dto.LabCcSettlementVoucherRequest;
import com.shopmanagement.ledgerservice.dto.PosSaleVoucherRequest;
import com.shopmanagement.ledgerservice.dto.ProfitAndLossResponse;
import com.shopmanagement.ledgerservice.dto.PurchaseDebitNoteVoucherRequest;
import com.shopmanagement.ledgerservice.dto.SalesInvoiceVoucherRequest;
import com.shopmanagement.ledgerservice.dto.SalesReturnVoucherRequest;
import com.shopmanagement.ledgerservice.dto.StockWriteOffVoucherRequest;
import com.shopmanagement.ledgerservice.dto.SupplierPaymentVoucherRequest;
import com.shopmanagement.ledgerservice.dto.TrialBalanceRow;
import com.shopmanagement.ledgerservice.model.LedgerAccount;
import com.shopmanagement.ledgerservice.model.LedgerVoucher;
import com.shopmanagement.ledgerservice.model.LedgerVoucherLine;
import com.shopmanagement.ledgerservice.service.AccountBookService;
import com.shopmanagement.ledgerservice.service.ApItcVoucherService;
import com.shopmanagement.ledgerservice.service.AccountsDashboardService;
import com.shopmanagement.ledgerservice.service.BankReconciliationService;
import com.shopmanagement.ledgerservice.service.CashVsProfitService;
import com.shopmanagement.ledgerservice.service.ChartOfAccountsService;
import com.shopmanagement.ledgerservice.service.CollectionReceiptVoucherService;
import com.shopmanagement.ledgerservice.service.CreditInterestVoucherService;
import com.shopmanagement.ledgerservice.service.FinalAccountsService;
import com.shopmanagement.ledgerservice.service.GoodsReceiptVoucherService;
import com.shopmanagement.ledgerservice.service.LabCcSettlementVoucherService;
import com.shopmanagement.ledgerservice.service.PeriodLockService;
import com.shopmanagement.ledgerservice.service.PosSaleVoucherService;
import com.shopmanagement.ledgerservice.service.PurchaseDebitNoteVoucherService;
import com.shopmanagement.ledgerservice.service.SalesInvoiceVoucherService;
import com.shopmanagement.ledgerservice.service.SalesReturnVoucherService;
import com.shopmanagement.ledgerservice.service.StockWriteOffVoucherService;
import com.shopmanagement.ledgerservice.service.SupplierPaymentVoucherService;
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
    private final PosSaleVoucherService posSaleVoucherService;
    private final SalesReturnVoucherService salesReturnVoucherService;
    private final CollectionReceiptVoucherService collectionReceiptVoucherService;
    private final CreditInterestVoucherService creditInterestVoucherService;
    private final GoodsReceiptVoucherService goodsReceiptVoucherService;
    private final ApItcVoucherService apItcVoucherService;
    private final PurchaseDebitNoteVoucherService purchaseDebitNoteVoucherService;
    private final SupplierPaymentVoucherService supplierPaymentVoucherService;
    private final AccountBookService accountBookService;
    private final AccountsDashboardService accountsDashboardService;
    private final FinalAccountsService finalAccountsService;
    private final StockWriteOffVoucherService stockWriteOffVoucherService;
    private final LabCcSettlementVoucherService labCcSettlementVoucherService;
    private final PeriodLockService periodLockService;
    private final BankReconciliationService bankReconciliationService;
    private final CashVsProfitService cashVsProfitService;

    public LedgerController(
            ChartOfAccountsService chartOfAccountsService,
            VoucherService voucherService,
            SalesInvoiceVoucherService salesInvoiceVoucherService,
            PosSaleVoucherService posSaleVoucherService,
            SalesReturnVoucherService salesReturnVoucherService,
            CollectionReceiptVoucherService collectionReceiptVoucherService,
            CreditInterestVoucherService creditInterestVoucherService,
            GoodsReceiptVoucherService goodsReceiptVoucherService,
            ApItcVoucherService apItcVoucherService,
            PurchaseDebitNoteVoucherService purchaseDebitNoteVoucherService,
            SupplierPaymentVoucherService supplierPaymentVoucherService,
            AccountBookService accountBookService,
            AccountsDashboardService accountsDashboardService,
            FinalAccountsService finalAccountsService,
            StockWriteOffVoucherService stockWriteOffVoucherService,
            LabCcSettlementVoucherService labCcSettlementVoucherService,
            PeriodLockService periodLockService,
            BankReconciliationService bankReconciliationService,
            CashVsProfitService cashVsProfitService) {
        this.chartOfAccountsService = chartOfAccountsService;
        this.voucherService = voucherService;
        this.salesInvoiceVoucherService = salesInvoiceVoucherService;
        this.posSaleVoucherService = posSaleVoucherService;
        this.salesReturnVoucherService = salesReturnVoucherService;
        this.collectionReceiptVoucherService = collectionReceiptVoucherService;
        this.creditInterestVoucherService = creditInterestVoucherService;
        this.goodsReceiptVoucherService = goodsReceiptVoucherService;
        this.apItcVoucherService = apItcVoucherService;
        this.purchaseDebitNoteVoucherService = purchaseDebitNoteVoucherService;
        this.supplierPaymentVoucherService = supplierPaymentVoucherService;
        this.accountBookService = accountBookService;
        this.accountsDashboardService = accountsDashboardService;
        this.finalAccountsService = finalAccountsService;
        this.stockWriteOffVoucherService = stockWriteOffVoucherService;
        this.labCcSettlementVoucherService = labCcSettlementVoucherService;
        this.periodLockService = periodLockService;
        this.bankReconciliationService = bankReconciliationService;
        this.cashVsProfitService = cashVsProfitService;
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
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer size) {
        return voucherService.list(from, to, type, size);
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

    /** Auto-post POS / clinic department bill → Cash|Bank|AR / Sales / GST (idempotent by order id). */
    @PostMapping("/vouchers/from-pos-sale")
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerVoucher fromPosSale(@RequestBody PosSaleVoucherRequest request) {
        return posSaleVoucherService.postFromPosSale(request);
    }

    /** Auto-post trade sales return → reverse AR/Sales (idempotent by sales return id). */
    @PostMapping("/vouchers/from-sales-return")
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerVoucher fromSalesReturn(@RequestBody SalesReturnVoucherRequest request) {
        return salesReturnVoucherService.postFromSalesReturn(request);
    }

    /** Auto-post trade collection → Cash/Bank Dr + Debtors Cr (idempotent by payment id). */
    @PostMapping("/vouchers/from-collection")
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerVoucher fromCollection(@RequestBody CollectionReceiptVoucherRequest request) {
        return collectionReceiptVoucherService.postFromCollection(request);
    }

    /**
     * Later collection of a credit POS / OPD / LAB / PHARM bill → same Dr Cash/Bank Cr Debtors.
     * Idempotent by {@code source_type=POS_COLLECTION} + payment id (not order id).
     */
    @PostMapping("/vouchers/from-pos-collection")
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerVoucher fromPosCollection(@RequestBody CollectionReceiptVoucherRequest request) {
        return collectionReceiptVoucherService.postFromPosCollection(request);
    }

    /** Auto-post overdue credit interest → Debtors Dr + Interest Income Cr. */
    @PostMapping("/vouchers/from-credit-interest")
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerVoucher fromCreditInterest(@RequestBody CreditInterestVoucherRequest request) {
        return creditInterestVoucherService.postFromCreditInterest(request);
    }

    /** Auto-post trade GRN → Stock Dr + Creditors Cr (idempotent by goods receipt id). */
    @PostMapping("/vouchers/from-goods-receipt")
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerVoucher fromGoodsReceipt(@RequestBody GoodsReceiptVoucherRequest request) {
        return goodsReceiptVoucherService.postFromGoodsReceipt(request);
    }

    /**
     * AP approve → Input CGST/SGST/IGST Dr + Creditors Cr for document tax only
     * ({@code source_type=AP_ITC}). Does not touch GRN stock.
     */
    @PostMapping("/vouchers/from-ap-itc")
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerVoucher fromApItc(@RequestBody ApItcVoucherRequest request) {
        return apItcVoucherService.postFromApInvoice(request);
    }

    /**
     * Purchase return ship → Dr Creditors (goods + tax), Cr Input GST, Cr Stock when
     * goods go back ({@code source_type=PURCHASE_DEBIT_NOTE}). Does not rewrite GRN / AP_ITC.
     */
    @PostMapping("/vouchers/from-purchase-debit-note")
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerVoucher fromPurchaseDebitNote(@RequestBody PurchaseDebitNoteVoucherRequest request) {
        return purchaseDebitNoteVoucherService.postFromPurchaseReturn(request);
    }

    /** Auto-post supplier payment → Creditors Dr + Cash/Bank Cr (idempotent by payment id). */
    @PostMapping("/vouchers/from-supplier-payment")
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerVoucher fromSupplierPayment(@RequestBody SupplierPaymentVoucherRequest request) {
        return supplierPaymentVoucherService.postFromSupplierPayment(request);
    }

    /** Dump / Brk / Exp write-off → Dr 5400 / Cr 1200 (idempotent by write-off id). */
    @PostMapping("/vouchers/from-stock-write-off")
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerVoucher fromStockWriteOff(@RequestBody StockWriteOffVoucherRequest request) {
        return stockWriteOffVoucherService.postFromStockWriteOff(request);
    }

    /** Path-lab CC settlement → Dr 5500 / Cr 2000 (+ Cr 2300 TDS) — idempotent by settlement run id. */
    @PostMapping("/vouchers/from-lab-cc-settlement")
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerVoucher fromLabCcSettlement(@RequestBody LabCcSettlementVoucherRequest request) {
        return labCcSettlementVoucherService.postFromLabCcSettlement(request);
    }

    @GetMapping("/trial-balance")
    public List<TrialBalanceRow> trialBalance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam(required = false) Long branchId) {
        return voucherService.trialBalance(asOf, branchId);
    }

    /** Final accounts — P&amp;L from posted INCOME/EXPENSE movement in period. */
    @GetMapping("/reports/profit-and-loss")
    public ProfitAndLossResponse profitAndLoss(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long branchId) {
        return finalAccountsService.profitAndLoss(from, to, branchId);
    }

    /** Cash movement (1000/1010) vs accrual net profit for the same period. */
    @GetMapping("/reports/cash-vs-profit")
    public CashVsProfitResponse cashVsProfit(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return cashVsProfitService.cashVsProfit(from, to);
    }

    /** Final accounts — Balance sheet as of date (TB + current-year P&amp;L equity plug). */
    @GetMapping("/reports/balance-sheet")
    public BalanceSheetResponse balanceSheet(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam(required = false) Long branchId) {
        return finalAccountsService.balanceSheet(asOf, branchId);
    }

    /** Cash (1000) or Bank (1010) book with opening + running balance. */
    @GetMapping("/cash-book")
    public AccountBookResponse cashBook(
            @RequestParam(defaultValue = "1000") String accountCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return accountBookService.book(accountCode, from, to);
    }

    @PatchMapping("/cash-book/lines/{lineId}/reconcile")
    public LedgerVoucherLine reconcileCashBookLine(
            @PathVariable Long lineId,
            @RequestBody Map<String, Object> body) {
        boolean reconciled = body == null || body.get("reconciled") == null
                || Boolean.parseBoolean(String.valueOf(body.get("reconciled")));
        return bankReconciliationService.setReconciled(lineId, reconciled);
    }

    @GetMapping("/period-lock")
    public Map<String, Object> getPeriodLock() {
        LocalDate locked = periodLockService.getLockedThrough();
        return Map.of("lockedThrough", locked != null ? locked.toString() : "");
    }

    @PostMapping("/period-lock")
    public Map<String, Object> setPeriodLock(@RequestBody Map<String, String> body) {
        String raw = body != null ? body.get("lockedThrough") : null;
        if (raw == null || raw.isBlank()) {
            periodLockService.clearLock();
            return Map.of("lockedThrough", "");
        }
        LocalDate locked = periodLockService.setLockedThrough(LocalDate.parse(raw.trim()));
        return Map.of("lockedThrough", locked.toString());
    }

}
