package com.shopmanagement.ledgerservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.ledgerservice.config.ExpenseModuleProperties;
import com.shopmanagement.ledgerservice.dto.ExpenseEntryRequest;
import com.shopmanagement.ledgerservice.model.ExpenseCategory;
import com.shopmanagement.ledgerservice.model.ExpenseEntry;
import com.shopmanagement.ledgerservice.model.LedgerAccount;
import com.shopmanagement.ledgerservice.model.LedgerVoucher;
import com.shopmanagement.ledgerservice.model.LedgerVoucherLine;
import com.shopmanagement.ledgerservice.repository.ExpenseCategoryRepository;
import com.shopmanagement.ledgerservice.repository.ExpenseEntryRepository;
import com.shopmanagement.ledgerservice.repository.LedgerAccountRepository;
import com.shopmanagement.ledgerservice.repository.LedgerVoucherRepository;

/**
 * Expense entries → balanced PAYMENT/JOURNAL vouchers (source_type EXPENSE_ENTRY).
 * MVP: finance role posts directly (status stays extensible for PENDING_APPROVAL later).
 */
@Service
public class ExpenseEntryService {

    public static final String SOURCE_EXPENSE = "EXPENSE_ENTRY";
    public static final String SOURCE_EXPENSE_VOID = "EXPENSE_VOID";
    public static final String CODE_CASH = "1000";
    public static final String CODE_BANK = "1010";
    public static final String CODE_CREDITORS = "2000";

    private static final Set<String> EDITABLE = Set.of("DRAFT", "PENDING_APPROVAL", "APPROVED");
    private static final Set<String> POSTABLE = Set.of("DRAFT", "APPROVED", "PENDING_APPROVAL");

    private final ExpenseEntryRepository entryRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final LedgerAccountRepository accountRepository;
    private final LedgerVoucherRepository voucherRepository;
    private final ChartOfAccountsService chartOfAccountsService;
    private final PeriodLockService periodLockService;
    private final ExpenseModuleProperties properties;

    public ExpenseEntryService(
            ExpenseEntryRepository entryRepository,
            ExpenseCategoryRepository categoryRepository,
            LedgerAccountRepository accountRepository,
            LedgerVoucherRepository voucherRepository,
            ChartOfAccountsService chartOfAccountsService,
            PeriodLockService periodLockService,
            ExpenseModuleProperties properties) {
        this.entryRepository = entryRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.voucherRepository = voucherRepository;
        this.chartOfAccountsService = chartOfAccountsService;
        this.periodLockService = periodLockService;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<ExpenseEntry> list(LocalDate from, LocalDate to, String status, Long categoryId) {
        assertEnabled();
        FinanceAccess.requireFinanceAccess();
        LocalDate fromDate = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate toDate = to != null ? to : LocalDate.now();
        String statusFilter = blank(status) ? "" : status.trim().toUpperCase(Locale.ROOT);
        return entryRepository.search(
                FinanceAccess.requireTenantId(),
                FinanceAccess.requireShopId(),
                fromDate,
                toDate,
                statusFilter,
                categoryId);
    }

    @Transactional(readOnly = true)
    public ExpenseEntry get(Long id) {
        assertEnabled();
        FinanceAccess.requireFinanceAccess();
        return entryRepository
                .findByIdAndTenantIdAndShopId(id, FinanceAccess.requireTenantId(), FinanceAccess.requireShopId())
                .orElseThrow(() -> new IllegalArgumentException("Expense not found: " + id));
    }

    @Transactional
    public ExpenseEntry create(ExpenseEntryRequest request) {
        assertEnabled();
        FinanceAccess.requireFinanceAccess();
        Long tenantId = FinanceAccess.requireTenantId();
        String shopId = FinanceAccess.requireShopId();
        validateRequest(request, tenantId, shopId);

        ExpenseEntry entry = new ExpenseEntry();
        entry.setTenantId(tenantId);
        entry.setShopId(shopId);
        entry.setBranchShopId(shopId);
        applyRequest(entry, request);
        entry.setStatus("DRAFT");
        entry.setSourceType(SOURCE_EXPENSE);
        entry.setRequestedBy(FinanceAccess.currentUsername());
        return entryRepository.save(entry);
    }

    @Transactional
    public ExpenseEntry update(Long id, ExpenseEntryRequest request) {
        assertEnabled();
        FinanceAccess.requireFinanceAccess();
        ExpenseEntry entry = get(id);
        if (!EDITABLE.contains(normalize(entry.getStatus()))) {
            throw new IllegalArgumentException("Only DRAFT/PENDING/APPROVED expenses can be edited");
        }
        Long tenantId = FinanceAccess.requireTenantId();
        String shopId = FinanceAccess.requireShopId();
        validateRequest(request, tenantId, shopId);
        applyRequest(entry, request);
        return entryRepository.save(entry);
    }

    /** Optional workflow hook — not required for MVP post-by-finance-role. */
    @Transactional
    public ExpenseEntry submit(Long id) {
        assertEnabled();
        FinanceAccess.requireFinanceAccess();
        ExpenseEntry entry = get(id);
        if (!"DRAFT".equalsIgnoreCase(entry.getStatus())) {
            throw new IllegalArgumentException("Only DRAFT expenses can be submitted");
        }
        entry.setStatus("PENDING_APPROVAL");
        return entryRepository.save(entry);
    }

    @Transactional
    public ExpenseEntry approve(Long id) {
        assertEnabled();
        FinanceAccess.requireFinanceAccess();
        ExpenseEntry entry = get(id);
        String status = normalize(entry.getStatus());
        if (!"PENDING_APPROVAL".equals(status) && !"DRAFT".equals(status)) {
            throw new IllegalArgumentException("Only DRAFT or PENDING_APPROVAL expenses can be approved");
        }
        entry.setStatus("APPROVED");
        entry.setApprovedBy(FinanceAccess.currentUsername());
        entry.setApprovedAt(LocalDateTime.now());
        return entryRepository.save(entry);
    }

    @Transactional
    public ExpenseEntry post(Long id) {
        assertEnabled();
        FinanceAccess.requireFinanceAccess();
        Long tenantId = FinanceAccess.requireTenantId();
        String shopId = FinanceAccess.requireShopId();
        ExpenseEntry entry = get(id);
        if ("POSTED".equalsIgnoreCase(entry.getStatus())) {
            return entry;
        }
        if ("VOID".equalsIgnoreCase(entry.getStatus())) {
            throw new IllegalArgumentException("Cannot post a voided expense");
        }
        if (!POSTABLE.contains(normalize(entry.getStatus()))) {
            throw new IllegalArgumentException("Expense status does not allow posting: " + entry.getStatus());
        }

        Optional<LedgerVoucher> existing = voucherRepository.findDetailedBySource(
                tenantId, shopId, SOURCE_EXPENSE, entry.getId());
        if (existing.isPresent()) {
            entry.setVoucherId(existing.get().getId());
            entry.setStatus("POSTED");
            return entryRepository.save(entry);
        }

        periodLockService.assertOpen(entry.getEntryDate());
        chartOfAccountsService.seedDefaults();

        ExpenseCategory category = categoryRepository
                .findByIdAndTenantIdAndShopId(entry.getCategoryId(), tenantId, shopId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + entry.getCategoryId()));
        LedgerAccount expenseAccount = accountRepository
                .findByIdAndTenantIdAndShopId(category.getLedgerAccountId(), tenantId, shopId)
                .orElseThrow(() -> new IllegalArgumentException("Expense ledger account missing"));

        double amount = round2(entry.getAmount().add(nz(entry.getTaxAmount())).doubleValue());
        if (amount <= 0) {
            throw new IllegalArgumentException("Expense amount must be greater than zero");
        }

        String mode = normalize(entry.getPaymentMode());
        String creditCode = resolveCreditAccountCode(mode, entry.getCashAccountCode());
        LedgerAccount creditAccount = requireAccount(tenantId, shopId, creditCode);
        String voucherType = "CREDIT".equals(mode) ? "JOURNAL" : "PAYMENT";

        LedgerVoucher voucher = new LedgerVoucher();
        voucher.setTenantId(tenantId);
        voucher.setShopId(shopId);
        voucher.setVoucherNumber("EXP-" + entry.getId());
        voucher.setVoucherDate(entry.getEntryDate());
        voucher.setVoucherType(voucherType);
        voucher.setStatus("POSTED");
        voucher.setPostedAt(LocalDateTime.now());
        voucher.setSourceType(SOURCE_EXPENSE);
        voucher.setSourceId(entry.getId());
        String narr = !blank(entry.getNarration())
                ? entry.getNarration()
                : "Expense " + category.getName()
                        + (blank(entry.getVendorName()) ? "" : " — " + entry.getVendorName());
        voucher.setNarration(narr);
        voucher.addLine(line(expenseAccount.getId(), amount, 0, category.getCode(), 1));
        voucher.addLine(line(creditAccount.getId(), 0, amount, mode + " " + creditCode, 2));
        voucher.setTotalDebit(amount);
        voucher.setTotalCredit(amount);
        if (!VoucherService.isBalanced(voucher.getTotalDebit(), voucher.getTotalCredit())) {
            throw new IllegalArgumentException("Expense voucher not balanced");
        }
        LedgerVoucher saved = voucherRepository.save(voucher);
        entry.setVoucherId(saved.getId());
        entry.setStatus("POSTED");
        return entryRepository.save(entry);
    }

    @Transactional
    public ExpenseEntry voidEntry(Long id) {
        assertEnabled();
        FinanceAccess.requireFinanceAccess();
        Long tenantId = FinanceAccess.requireTenantId();
        String shopId = FinanceAccess.requireShopId();
        ExpenseEntry entry = get(id);
        if ("VOID".equalsIgnoreCase(entry.getStatus())) {
            return entry;
        }
        if (!"POSTED".equalsIgnoreCase(entry.getStatus())) {
            entry.setStatus("VOID");
            return entryRepository.save(entry);
        }

        Optional<LedgerVoucher> existingVoid = voucherRepository.findDetailedBySource(
                tenantId, shopId, SOURCE_EXPENSE_VOID, entry.getId());
        if (existingVoid.isPresent()) {
            entry.setStatus("VOID");
            return entryRepository.save(entry);
        }

        LedgerVoucher original = voucherRepository
                .findDetailedByIdAndTenantIdAndShopId(entry.getVoucherId(), tenantId, shopId)
                .orElseThrow(() -> new IllegalArgumentException("Posted voucher not found for expense"));
        periodLockService.assertOpen(LocalDate.now());

        LedgerVoucher reverse = new LedgerVoucher();
        reverse.setTenantId(tenantId);
        reverse.setShopId(shopId);
        reverse.setVoucherNumber("EXP-VOID-" + entry.getId());
        reverse.setVoucherDate(LocalDate.now());
        reverse.setVoucherType("JOURNAL");
        reverse.setStatus("POSTED");
        reverse.setPostedAt(LocalDateTime.now());
        reverse.setSourceType(SOURCE_EXPENSE_VOID);
        reverse.setSourceId(entry.getId());
        reverse.setNarration("Void expense #" + entry.getId() + " (reverses " + original.getVoucherNumber() + ")");
        int lineNo = 1;
        double debit = 0;
        double credit = 0;
        for (LedgerVoucherLine ol : original.getLines()) {
            // Swap debit/credit
            reverse.addLine(line(ol.getAccountId(), safe(ol.getCredit()), safe(ol.getDebit()),
                    "Reversal", lineNo++));
            debit += safe(ol.getCredit());
            credit += safe(ol.getDebit());
        }
        reverse.setTotalDebit(round2(debit));
        reverse.setTotalCredit(round2(credit));
        voucherRepository.save(reverse);
        entry.setStatus("VOID");
        return entryRepository.save(entry);
    }

    private void validateRequest(ExpenseEntryRequest request, Long tenantId, String shopId) {
        if (request == null || request.getCategoryId() == null || request.getAmount() == null
                || blank(request.getPaymentMode())) {
            throw new IllegalArgumentException("categoryId, amount and paymentMode are required");
        }
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
        categoryRepository
                .findByIdAndTenantIdAndShopId(request.getCategoryId(), tenantId, shopId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown category: " + request.getCategoryId()));
        String mode = normalize(request.getPaymentMode());
        if (!Set.of("CASH", "BANK", "UPI", "CREDIT").contains(mode)) {
            throw new IllegalArgumentException("paymentMode must be CASH|BANK|UPI|CREDIT");
        }
    }

    private void applyRequest(ExpenseEntry entry, ExpenseEntryRequest request) {
        entry.setEntryDate(request.getEntryDate() != null ? request.getEntryDate() : LocalDate.now());
        entry.setCategoryId(request.getCategoryId());
        entry.setAmount(request.getAmount().setScale(2, RoundingMode.HALF_UP));
        entry.setTaxAmount(request.getTaxAmount() != null
                ? request.getTaxAmount().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        entry.setCurrency(blank(request.getCurrency()) ? "INR" : request.getCurrency().trim().toUpperCase(Locale.ROOT));
        entry.setPaymentMode(normalize(request.getPaymentMode()));
        entry.setCashAccountCode(trimToNull(request.getCashAccountCode()));
        entry.setVendorName(trimToNull(request.getVendorName()));
        entry.setNarration(trimToNull(request.getNarration()));
        entry.setAttachmentUri(trimToNull(request.getAttachmentUri()));
    }

    private String resolveCreditAccountCode(String mode, String override) {
        if (!blank(override)) {
            return override.trim();
        }
        return switch (mode) {
            case "CASH" -> CODE_CASH;
            case "CREDIT" -> CODE_CREDITORS;
            default -> CODE_BANK; // BANK, UPI
        };
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

    private void assertEnabled() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Expense module is disabled (ledger.expense-module.enabled=false)");
        }
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static double safe(Double value) {
        return value == null ? 0.0 : value;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
