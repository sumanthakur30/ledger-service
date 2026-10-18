package com.shopmanagement.ledgerservice.service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.shopmanagement.ledgerservice.dto.BankReconDtos.BatchSummary;
import com.shopmanagement.ledgerservice.dto.BankReconDtos.MatchResponse;
import com.shopmanagement.ledgerservice.dto.BankReconDtos.MatchRow;
import com.shopmanagement.ledgerservice.filter.RequestIdFilter;
import com.shopmanagement.ledgerservice.model.BankStatementImportBatch;
import com.shopmanagement.ledgerservice.model.BankStatementLine;
import com.shopmanagement.ledgerservice.model.LedgerAccount;
import com.shopmanagement.ledgerservice.model.LedgerVoucher;
import com.shopmanagement.ledgerservice.model.LedgerVoucherLine;
import com.shopmanagement.ledgerservice.repository.BankStatementImportBatchRepository;
import com.shopmanagement.ledgerservice.repository.BankStatementLineRepository;
import com.shopmanagement.ledgerservice.repository.LedgerAccountRepository;
import com.shopmanagement.ledgerservice.repository.LedgerVoucherRepository;
import com.shopmanagement.ledgerservice.service.BankReconMatchMath.BooksLine;
import com.shopmanagement.ledgerservice.service.BankReconMatchMath.StatementLine;
import com.shopmanagement.ledgerservice.service.BankStatementCsvParser.ParseResult;
import com.shopmanagement.ledgerservice.service.BankStatementCsvParser.ParsedLine;

/**
 * CSV bank/cash statement import + propose matches. Ticks go through {@link BankReconciliationService}.
 */
@Service
public class BankStatementImportService {

    private static final long MAX_BYTES = 4L * 1024 * 1024;
    private static final Set<String> BOOK_CODES = Set.of("1000", "1010");

    private final BankStatementImportBatchRepository batchRepository;
    private final BankStatementLineRepository lineRepository;
    private final LedgerAccountRepository accountRepository;
    private final LedgerVoucherRepository voucherRepository;
    private final BankReconciliationService bankReconciliationService;

    public BankStatementImportService(
            BankStatementImportBatchRepository batchRepository,
            BankStatementLineRepository lineRepository,
            LedgerAccountRepository accountRepository,
            LedgerVoucherRepository voucherRepository,
            BankReconciliationService bankReconciliationService) {
        this.batchRepository = batchRepository;
        this.lineRepository = lineRepository;
        this.accountRepository = accountRepository;
        this.voucherRepository = voucherRepository;
        this.bankReconciliationService = bankReconciliationService;
    }

    @Transactional
    public MatchResponse importCsv(MultipartFile file, String accountCode, LocalDate from, LocalDate to) {
        requireAccess();
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("CSV file is required");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("File exceeds 4 MB");
        }
        String code = requireBookCode(accountCode);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Could not read upload: " + ex.getMessage());
        }
        ParseResult parsed = BankStatementCsvParser.parse(file.getOriginalFilename(), bytes);
        if (parsed.lines().isEmpty()) {
            throw new IllegalArgumentException("No statement lines found in CSV");
        }

        Long tenantId = requireTenantId();
        String shopId = requireShopId();
        LedgerAccount account = requireAccount(tenantId, shopId, code);
        LocalDate fromDate = from != null ? from : inferredFrom(parsed.lines());
        LocalDate toDate = to != null ? to : inferredTo(parsed.lines());
        if (toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("to must be on or after from");
        }

        BankStatementImportBatch batch = new BankStatementImportBatch();
        batch.setTenantId(tenantId);
        batch.setShopId(shopId);
        batch.setAccountCode(code);
        batch.setFileName(clip(file.getOriginalFilename(), 255));
        batch.setFromDate(fromDate);
        batch.setToDate(toDate);
        batch.setLineCount(parsed.lines().size());
        batch.setSkippedCount(parsed.skipped());
        batch.setCreatedAt(LocalDateTime.now());
        batch.setCreatedBy(RequestIdFilter.getCurrentUsername());
        batch = batchRepository.save(batch);

        List<BankStatementLine> saved = new ArrayList<>();
        int lineNo = 1;
        for (ParsedLine parsedLine : parsed.lines()) {
            BankStatementLine entity = new BankStatementLine();
            entity.setBatchId(batch.getId());
            entity.setTenantId(tenantId);
            entity.setShopId(shopId);
            entity.setLineNo(lineNo++);
            entity.setTxnDate(parsedLine.txnDate());
            entity.setValueDate(parsedLine.valueDate());
            entity.setDescription(parsedLine.description());
            entity.setReference(parsedLine.reference());
            entity.setDebit(parsedLine.debit());
            entity.setCredit(parsedLine.credit());
            entity.setBalance(parsedLine.balance());
            saved.add(entity);
        }
        saved = lineRepository.saveAll(saved);
        return rematch(batch, saved, account, parsed.warnings());
    }

    @Transactional(readOnly = true)
    public List<BatchSummary> listBatches(String accountCode) {
        requireAccess();
        Long tenantId = requireTenantId();
        String shopId = requireShopId();
        List<BankStatementImportBatch> batches;
        if (accountCode != null && !accountCode.isBlank()) {
            batches = batchRepository.findByTenantIdAndShopIdAndAccountCodeOrderByCreatedAtDescIdDesc(
                    tenantId, shopId, requireBookCode(accountCode));
        } else {
            batches = batchRepository.findByTenantIdAndShopIdOrderByCreatedAtDescIdDesc(tenantId, shopId);
        }
        return batches.stream().limit(20).map(this::toSummary).toList();
    }

    @Transactional
    public MatchResponse getBatch(Long batchId) {
        requireAccess();
        BankStatementImportBatch batch = requireBatch(batchId);
        LedgerAccount account = requireAccount(batch.getTenantId(), batch.getShopId(), batch.getAccountCode());
        List<BankStatementLine> lines = lineRepository.findByBatchIdOrderByLineNoAsc(batch.getId());
        return rematch(batch, lines, account, List.of());
    }

    /**
     * Tick existing {@code reconciled} on MATCHED books lines. Idempotent; reverse with the cash-book untick API.
     */
    @Transactional
    public MatchResponse confirmMatched(Long batchId) {
        requireAccess();
        BankStatementImportBatch batch = requireBatch(batchId);
        LedgerAccount account = requireAccount(batch.getTenantId(), batch.getShopId(), batch.getAccountCode());
        List<BankStatementLine> lines = lineRepository.findByBatchIdOrderByLineNoAsc(batch.getId());
        MatchResponse proposed = rematch(batch, lines, account, List.of());
        for (MatchRow row : proposed.rows()) {
            if (!BankReconMatchMath.MATCHED.equals(row.status()) || row.booksLineId() == null) {
                continue;
            }
            if (row.booksReconciled()) {
                continue;
            }
            bankReconciliationService.setReconciled(row.booksLineId(), true);
        }
        lines = lineRepository.findByBatchIdOrderByLineNoAsc(batch.getId());
        return rematch(batch, lines, account, List.of("Ticked MATCHED lines via existing recon flag. Untick on the cash/bank book to reverse."));
    }

    private MatchResponse rematch(
            BankStatementImportBatch batch,
            List<BankStatementLine> lines,
            LedgerAccount account,
            List<String> extraNotes) {
        LocalDate from = batch.getFromDate() != null ? batch.getFromDate() : LocalDate.now().withDayOfMonth(1);
        LocalDate to = batch.getToDate() != null ? batch.getToDate() : LocalDate.now();
        List<BooksLine> books = loadBooks(batch.getTenantId(), batch.getShopId(), account.getId(), from, to);
        List<StatementLine> stmts = new ArrayList<>();
        for (BankStatementLine line : lines) {
            stmts.add(new StatementLine(
                    line.getLineNo(),
                    line.getId(),
                    line.getTxnDate() != null ? line.getTxnDate() : line.getValueDate(),
                    line.getDescription(),
                    line.getReference(),
                    nz(line.getDebit()),
                    nz(line.getCredit())));
        }
        List<BankReconMatchMath.MatchRow> mathRows = BankReconMatchMath.match(stmts, books);
        int matched = 0;
        int amountMismatch = 0;
        int dateMismatch = 0;
        int statementOnly = 0;
        int booksOnly = 0;
        List<MatchRow> rows = new ArrayList<>();
        for (BankReconMatchMath.MatchRow math : mathRows) {
            switch (math.status()) {
                case BankReconMatchMath.MATCHED -> matched++;
                case BankReconMatchMath.AMOUNT_MISMATCH -> amountMismatch++;
                case BankReconMatchMath.DATE_MISMATCH -> dateMismatch++;
                case BankReconMatchMath.STATEMENT_ONLY -> statementOnly++;
                case BankReconMatchMath.BOOKS_ONLY -> booksOnly++;
                default -> {
                }
            }
            if (math.statementLineId() != null && math.statementLineNo() != null) {
                persistMatch(lines, math);
            }
            rows.add(toDto(math));
        }
        lineRepository.saveAll(lines);

        String disclaimer = "File import only — not a live bank feed. Match proposes; tick MATCHED to set the existing recon flag (±₹1 / ±1 day).";
        if (extraNotes != null && !extraNotes.isEmpty()) {
            disclaimer = disclaimer + " " + String.join(" ", extraNotes);
        }
        return new MatchResponse(
                batch.getId(),
                account.getCode(),
                account.getName(),
                batch.getFileName(),
                from,
                to,
                lines.size(),
                batch.getSkippedCount(),
                matched,
                amountMismatch,
                dateMismatch,
                statementOnly,
                booksOnly,
                rows,
                disclaimer);
    }

    private void persistMatch(List<BankStatementLine> lines, BankReconMatchMath.MatchRow math) {
        for (BankStatementLine line : lines) {
            if (line.getId() != null && line.getId().equals(math.statementLineId())) {
                line.setMatchStatus(math.status());
                line.setMatchedLineId(math.booksLineId());
                return;
            }
        }
    }

    private List<BooksLine> loadBooks(Long tenantId, String shopId, Long accountId, LocalDate from, LocalDate to) {
        LocalDate lookFrom = from.minusDays(BankReconMatchMath.DATE_TOLERANCE_DAYS);
        LocalDate lookTo = to.plusDays(BankReconMatchMath.DATE_TOLERANCE_DAYS);
        List<LedgerVoucher> period = voucherRepository.search(tenantId, shopId, lookFrom, lookTo, "");
        period.sort(Comparator
                .comparing(LedgerVoucher::getVoucherDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(LedgerVoucher::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        List<BooksLine> books = new ArrayList<>();
        for (LedgerVoucher voucher : period) {
            if (!"POSTED".equalsIgnoreCase(voucher.getStatus()) || voucher.getLines() == null) {
                continue;
            }
            for (LedgerVoucherLine line : voucher.getLines()) {
                if (line.getAccountId() == null || !line.getAccountId().equals(accountId)) {
                    continue;
                }
                double debit = nz(line.getDebit());
                double credit = nz(line.getCredit());
                if (debit <= 0.009 && credit <= 0.009) {
                    continue;
                }
                String narr = line.getLineNarration();
                if (narr == null || narr.isBlank()) {
                    narr = voucher.getNarration();
                }
                books.add(new BooksLine(
                        line.getId(),
                        voucher.getId(),
                        voucher.getVoucherNumber(),
                        voucher.getVoucherDate(),
                        narr,
                        voucher.getSourceType(),
                        voucher.getSourceId(),
                        debit,
                        credit,
                        Boolean.TRUE.equals(line.getReconciled())));
            }
        }
        return books;
    }

    private BankStatementImportBatch requireBatch(Long batchId) {
        if (batchId == null) {
            throw new IllegalArgumentException("batchId is required");
        }
        return batchRepository
                .findByIdAndTenantIdAndShopId(batchId, requireTenantId(), requireShopId())
                .orElseThrow(() -> new IllegalArgumentException("Import batch not found: " + batchId));
    }

    private LedgerAccount requireAccount(Long tenantId, String shopId, String code) {
        return accountRepository
                .findByTenantIdAndShopIdAndCode(tenantId, shopId, code)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Account " + code + " not found — seed chart of accounts defaults first"));
    }

    private static String requireBookCode(String accountCode) {
        String code = accountCode == null ? "" : accountCode.trim();
        if (!BOOK_CODES.contains(code)) {
            throw new IllegalArgumentException("accountCode must be 1000 (Cash) or 1010 (Bank)");
        }
        return code;
    }

    private BatchSummary toSummary(BankStatementImportBatch batch) {
        return new BatchSummary(
                batch.getId(),
                batch.getAccountCode(),
                batch.getFileName(),
                batch.getFromDate(),
                batch.getToDate(),
                batch.getLineCount(),
                batch.getSkippedCount(),
                batch.getCreatedAt(),
                batch.getCreatedBy());
    }

    private static MatchRow toDto(BankReconMatchMath.MatchRow math) {
        return new MatchRow(
                math.status(),
                math.statementLineNo(),
                math.statementLineId(),
                math.booksLineId(),
                math.voucherId(),
                math.voucherNumber(),
                math.statementDate(),
                math.booksDate(),
                math.statementDescription(),
                math.booksNarration(),
                math.statementRef(),
                math.statementDebit(),
                math.statementCredit(),
                math.booksDebit(),
                math.booksCredit(),
                math.booksReconciled(),
                math.note());
    }

    private static LocalDate inferredFrom(List<ParsedLine> lines) {
        return lines.stream()
                .map(ParsedLine::txnDate)
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now().withDayOfMonth(1));
    }

    private static LocalDate inferredTo(List<ParsedLine> lines) {
        return lines.stream()
                .map(ParsedLine::txnDate)
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now());
    }

    private static double nz(Double value) {
        return value == null ? 0.0 : value;
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        String t = value.trim();
        return t.length() <= max ? t : t.substring(0, max);
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

    private void requireAccess() {
        String role = RequestIdFilter.getCurrentRole();
        if (role != null) {
            String r = role.trim().toUpperCase(Locale.ROOT);
            if ("SUPER_ADMIN".equals(r) || "SHOP_OWNER".equals(r) || "TRADE_ACCOUNTANT".equals(r)) {
                return;
            }
        }
        var perms = RequestIdFilter.getCurrentPermissions();
        if (perms.contains("MANAGE_ORDERS") || perms.contains("PROCUREMENT_FINANCE")
                || perms.contains("MANAGE_FINANCE")) {
            return;
        }
        throw new SecurityException("Forbidden: missing permission MANAGE_ORDERS");
    }
}
