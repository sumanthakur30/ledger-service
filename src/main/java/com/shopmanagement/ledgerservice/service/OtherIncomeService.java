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
import com.shopmanagement.ledgerservice.dto.OtherIncomeRequest;
import com.shopmanagement.ledgerservice.model.IncomeType;
import com.shopmanagement.ledgerservice.model.LedgerAccount;
import com.shopmanagement.ledgerservice.model.LedgerVoucher;
import com.shopmanagement.ledgerservice.model.LedgerVoucherLine;
import com.shopmanagement.ledgerservice.model.OtherIncomeEntry;
import com.shopmanagement.ledgerservice.repository.IncomeTypeRepository;
import com.shopmanagement.ledgerservice.repository.LedgerAccountRepository;
import com.shopmanagement.ledgerservice.repository.LedgerVoucherRepository;
import com.shopmanagement.ledgerservice.repository.OtherIncomeEntryRepository;

/** Other income entries → RECEIPT/JOURNAL vouchers (source_type OTHER_INCOME). */
@Service
public class OtherIncomeService {

    public static final String SOURCE_OTHER_INCOME = "OTHER_INCOME";
    public static final String SOURCE_OTHER_INCOME_VOID = "OTHER_INCOME_VOID";
    public static final String CODE_CASH = "1000";
    public static final String CODE_BANK = "1010";
    public static final String CODE_DEBTORS = "1100";

    private static final Set<String> EDITABLE = Set.of("DRAFT", "PENDING_APPROVAL", "APPROVED");
    private static final Set<String> POSTABLE = Set.of("DRAFT", "APPROVED", "PENDING_APPROVAL");

    private final OtherIncomeEntryRepository entryRepository;
    private final IncomeTypeRepository incomeTypeRepository;
    private final LedgerAccountRepository accountRepository;
    private final LedgerVoucherRepository voucherRepository;
    private final ChartOfAccountsService chartOfAccountsService;
    private final PeriodLockService periodLockService;
    private final ExpenseModuleProperties properties;

    public OtherIncomeService(
            OtherIncomeEntryRepository entryRepository,
            IncomeTypeRepository incomeTypeRepository,
            LedgerAccountRepository accountRepository,
            LedgerVoucherRepository voucherRepository,
            ChartOfAccountsService chartOfAccountsService,
            PeriodLockService periodLockService,
            ExpenseModuleProperties properties) {
        this.entryRepository = entryRepository;
        this.incomeTypeRepository = incomeTypeRepository;
        this.accountRepository = accountRepository;
        this.voucherRepository = voucherRepository;
        this.chartOfAccountsService = chartOfAccountsService;
        this.periodLockService = periodLockService;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<OtherIncomeEntry> list(LocalDate from, LocalDate to, String status) {
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
                statusFilter);
    }

    @Transactional(readOnly = true)
    public OtherIncomeEntry get(Long id) {
        assertEnabled();
        FinanceAccess.requireFinanceAccess();
        return entryRepository
                .findByIdAndTenantIdAndShopId(id, FinanceAccess.requireTenantId(), FinanceAccess.requireShopId())
                .orElseThrow(() -> new IllegalArgumentException("Other income not found: " + id));
    }

    @Transactional
    public OtherIncomeEntry create(OtherIncomeRequest request) {
        assertEnabled();
        FinanceAccess.requireFinanceAccess();
        Long tenantId = FinanceAccess.requireTenantId();
        String shopId = FinanceAccess.requireShopId();
        IncomeType type = validateAndResolveType(request, tenantId, shopId);

        OtherIncomeEntry entry = new OtherIncomeEntry();
        entry.setTenantId(tenantId);
        entry.setShopId(shopId);
        entry.setBranchShopId(shopId);
        applyRequest(entry, request, type);
        entry.setStatus("DRAFT");
        entry.setSourceType(SOURCE_OTHER_INCOME);
        return entryRepository.save(entry);
    }

    @Transactional
    public OtherIncomeEntry update(Long id, OtherIncomeRequest request) {
        assertEnabled();
        FinanceAccess.requireFinanceAccess();
        OtherIncomeEntry entry = get(id);
        if (!EDITABLE.contains(normalize(entry.getStatus()))) {
            throw new IllegalArgumentException("Only DRAFT/PENDING/APPROVED other income can be edited");
        }
        Long tenantId = FinanceAccess.requireTenantId();
        String shopId = FinanceAccess.requireShopId();
        IncomeType type = validateAndResolveType(request, tenantId, shopId);
        applyRequest(entry, request, type);
        return entryRepository.save(entry);
    }

    @Transactional
    public OtherIncomeEntry post(Long id) {
        assertEnabled();
        FinanceAccess.requireFinanceAccess();
        Long tenantId = FinanceAccess.requireTenantId();
        String shopId = FinanceAccess.requireShopId();
        OtherIncomeEntry entry = get(id);
        if ("POSTED".equalsIgnoreCase(entry.getStatus())) {
            return entry;
        }
        if ("VOID".equalsIgnoreCase(entry.getStatus())) {
            throw new IllegalArgumentException("Cannot post a voided other income entry");
        }
        if (!POSTABLE.contains(normalize(entry.getStatus()))) {
            throw new IllegalArgumentException("Status does not allow posting: " + entry.getStatus());
        }

        Optional<LedgerVoucher> existing = voucherRepository.findDetailedBySource(
                tenantId, shopId, SOURCE_OTHER_INCOME, entry.getId());
        if (existing.isPresent()) {
            entry.setVoucherId(existing.get().getId());
            entry.setStatus("POSTED");
            return entryRepository.save(entry);
        }

        periodLockService.assertOpen(entry.getEntryDate());
        chartOfAccountsService.seedDefaults();

        LedgerAccount incomeAccount = accountRepository
                .findByIdAndTenantIdAndShopId(entry.getLedgerAccountId(), tenantId, shopId)
                .orElseThrow(() -> new IllegalArgumentException("Income ledger account missing"));
        double amount = round2(entry.getAmount().doubleValue());
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        String mode = normalize(entry.getPaymentMode());
        String debitCode = resolveDebitAccountCode(mode, entry.getCashAccountCode());
        LedgerAccount debitAccount = requireAccount(tenantId, shopId, debitCode);
        String voucherType = "AR".equals(mode) ? "JOURNAL" : "RECEIPT";

        LedgerVoucher voucher = new LedgerVoucher();
        voucher.setTenantId(tenantId);
        voucher.setShopId(shopId);
        voucher.setVoucherNumber("OI-" + entry.getId());
        voucher.setVoucherDate(entry.getEntryDate());
        voucher.setVoucherType(voucherType);
        voucher.setStatus("POSTED");
        voucher.setPostedAt(LocalDateTime.now());
        voucher.setSourceType(SOURCE_OTHER_INCOME);
        voucher.setSourceId(entry.getId());
        String narr = !blank(entry.getNarration())
                ? entry.getNarration()
                : "Other income " + entry.getIncomeTypeCode()
                        + (blank(entry.getPayerName()) ? "" : " — " + entry.getPayerName());
        voucher.setNarration(narr);
        voucher.addLine(line(debitAccount.getId(), amount, 0, mode + " " + debitCode, 1));
        voucher.addLine(line(incomeAccount.getId(), 0, amount, entry.getIncomeTypeCode(), 2));
        voucher.setTotalDebit(amount);
        voucher.setTotalCredit(amount);
        if (!VoucherService.isBalanced(voucher.getTotalDebit(), voucher.getTotalCredit())) {
            throw new IllegalArgumentException("Other income voucher not balanced");
        }
        LedgerVoucher saved = voucherRepository.save(voucher);
        entry.setVoucherId(saved.getId());
        entry.setStatus("POSTED");
        return entryRepository.save(entry);
    }

    @Transactional
    public OtherIncomeEntry voidEntry(Long id) {
        assertEnabled();
        FinanceAccess.requireFinanceAccess();
        Long tenantId = FinanceAccess.requireTenantId();
        String shopId = FinanceAccess.requireShopId();
        OtherIncomeEntry entry = get(id);
        if ("VOID".equalsIgnoreCase(entry.getStatus())) {
            return entry;
        }
        if (!"POSTED".equalsIgnoreCase(entry.getStatus())) {
            entry.setStatus("VOID");
            return entryRepository.save(entry);
        }

        Optional<LedgerVoucher> existingVoid = voucherRepository.findDetailedBySource(
                tenantId, shopId, SOURCE_OTHER_INCOME_VOID, entry.getId());
        if (existingVoid.isPresent()) {
            entry.setStatus("VOID");
            return entryRepository.save(entry);
        }

        LedgerVoucher original = voucherRepository
                .findDetailedByIdAndTenantIdAndShopId(entry.getVoucherId(), tenantId, shopId)
                .orElseThrow(() -> new IllegalArgumentException("Posted voucher not found"));
        periodLockService.assertOpen(LocalDate.now());

        LedgerVoucher reverse = new LedgerVoucher();
        reverse.setTenantId(tenantId);
        reverse.setShopId(shopId);
        reverse.setVoucherNumber("OI-VOID-" + entry.getId());
        reverse.setVoucherDate(LocalDate.now());
        reverse.setVoucherType("JOURNAL");
        reverse.setStatus("POSTED");
        reverse.setPostedAt(LocalDateTime.now());
        reverse.setSourceType(SOURCE_OTHER_INCOME_VOID);
        reverse.setSourceId(entry.getId());
        reverse.setNarration("Void other income #" + entry.getId() + " (reverses " + original.getVoucherNumber() + ")");
        int lineNo = 1;
        double debit = 0;
        double credit = 0;
        for (LedgerVoucherLine ol : original.getLines()) {
            reverse.addLine(line(ol.getAccountId(), safe(ol.getCredit()), safe(ol.getDebit()), "Reversal", lineNo++));
            debit += safe(ol.getCredit());
            credit += safe(ol.getDebit());
        }
        reverse.setTotalDebit(round2(debit));
        reverse.setTotalCredit(round2(credit));
        voucherRepository.save(reverse);
        entry.setStatus("VOID");
        return entryRepository.save(entry);
    }

    private IncomeType validateAndResolveType(OtherIncomeRequest request, Long tenantId, String shopId) {
        if (request == null || blank(request.getIncomeTypeCode()) || request.getAmount() == null
                || blank(request.getPaymentMode())) {
            throw new IllegalArgumentException("incomeTypeCode, amount and paymentMode are required");
        }
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
        String mode = normalize(request.getPaymentMode());
        if (!Set.of("CASH", "BANK", "UPI", "AR").contains(mode)) {
            throw new IllegalArgumentException("paymentMode must be CASH|BANK|UPI|AR");
        }
        return incomeTypeRepository
                .findByTenantIdAndShopIdAndCode(tenantId, shopId, request.getIncomeTypeCode().trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown income type: " + request.getIncomeTypeCode() + " — seed defaults first"));
    }

    private void applyRequest(OtherIncomeEntry entry, OtherIncomeRequest request, IncomeType type) {
        entry.setEntryDate(request.getEntryDate() != null ? request.getEntryDate() : LocalDate.now());
        entry.setIncomeTypeCode(type.getCode());
        entry.setLedgerAccountId(type.getLedgerAccountId());
        entry.setAmount(request.getAmount().setScale(2, RoundingMode.HALF_UP));
        entry.setPaymentMode(normalize(request.getPaymentMode()));
        entry.setCashAccountCode(trimToNull(request.getCashAccountCode()));
        entry.setPayerName(trimToNull(request.getPayerName()));
        entry.setNarration(trimToNull(request.getNarration()));
    }

    private String resolveDebitAccountCode(String mode, String override) {
        if (!blank(override)) {
            return override.trim();
        }
        return switch (mode) {
            case "CASH" -> CODE_CASH;
            case "AR" -> CODE_DEBTORS;
            default -> CODE_BANK;
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
