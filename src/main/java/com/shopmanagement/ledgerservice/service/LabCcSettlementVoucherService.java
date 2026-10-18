package com.shopmanagement.ledgerservice.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.ledgerservice.dto.LabCcSettlementVoucherRequest;
import com.shopmanagement.ledgerservice.filter.RequestIdFilter;
import com.shopmanagement.ledgerservice.model.LedgerAccount;
import com.shopmanagement.ledgerservice.model.LedgerVoucher;
import com.shopmanagement.ledgerservice.model.LedgerVoucherLine;
import com.shopmanagement.ledgerservice.repository.LedgerAccountRepository;
import com.shopmanagement.ledgerservice.repository.LedgerVoucherRepository;

/**
 * Dr 5500 CC / outsource expense (gross);
 * Cr 2000 Creditors (net payable);
 * Cr 2300 TDS Payable (withholding) when TDS &gt; 0.
 */
@Service
public class LabCcSettlementVoucherService {

    public static final String SOURCE_LAB_CC_SETTLEMENT = "LAB_CC_SETTLEMENT";
    public static final String CODE_CREDITORS = "2000";
    public static final String CODE_TDS_PAYABLE = "2300";
    public static final String CODE_CC_EXPENSE = "5500";

    private final LedgerVoucherRepository voucherRepository;
    private final LedgerAccountRepository accountRepository;
    private final ChartOfAccountsService chartOfAccountsService;

    public LabCcSettlementVoucherService(
            LedgerVoucherRepository voucherRepository,
            LedgerAccountRepository accountRepository,
            ChartOfAccountsService chartOfAccountsService) {
        this.voucherRepository = voucherRepository;
        this.accountRepository = accountRepository;
        this.chartOfAccountsService = chartOfAccountsService;
    }

    @Transactional
    public LedgerVoucher postFromLabCcSettlement(LabCcSettlementVoucherRequest request) {
        if (request == null || request.getSettlementRunId() == null) {
            throw new IllegalArgumentException("settlementRunId is required");
        }
        Long tenantId = requireTenantId();
        String shopId = requireShopId();

        Optional<LedgerVoucher> existing = voucherRepository.findDetailedBySource(
                tenantId, shopId, SOURCE_LAB_CC_SETTLEMENT, request.getSettlementRunId());
        if (existing.isPresent()) {
            return existing.get();
        }

        double gross = round2(safe(request.getGrossAmount()));
        double tds = round2(safe(request.getTdsAmount()));
        double net = round2(safe(request.getNetAmount()));
        if (gross <= 0) {
            throw new IllegalArgumentException("grossAmount must be greater than zero");
        }
        if (net <= 0 && tds <= 0) {
            throw new IllegalArgumentException("netAmount or tdsAmount required");
        }
        if (Math.abs((net + tds) - gross) > 0.05) {
            net = round2(gross - tds);
        }

        chartOfAccountsService.seedDefaults();
        ensureAccount(tenantId, shopId, CODE_CC_EXPENSE, "Collection centre / outsource expense", "EXPENSE");
        ensureAccount(tenantId, shopId, CODE_TDS_PAYABLE, "TDS Payable", "LIABILITY");
        LedgerAccount expense = requireAccount(tenantId, shopId, CODE_CC_EXPENSE);
        LedgerAccount creditors = requireAccount(tenantId, shopId, CODE_CREDITORS);
        LedgerAccount tdsPayable = requireAccount(tenantId, shopId, CODE_TDS_PAYABLE);

        LedgerVoucher voucher = new LedgerVoucher();
        voucher.setTenantId(tenantId);
        voucher.setShopId(shopId);
        voucher.setBranchId(TrialBalanceMath.normalize(request.getBranchId()));
        voucher.setVoucherNumber("LCC-" + request.getSettlementRunId());
        voucher.setVoucherDate(request.getVoucherDate() != null ? request.getVoucherDate() : LocalDate.now());
        voucher.setVoucherType("JOURNAL");
        voucher.setStatus("POSTED");
        voucher.setPostedAt(LocalDateTime.now());
        voucher.setSourceType(SOURCE_LAB_CC_SETTLEMENT);
        voucher.setSourceId(request.getSettlementRunId());
        voucher.setNarration(
                request.getNarration() != null && !request.getNarration().isBlank()
                        ? request.getNarration().trim()
                        : "Path lab CC settlement #" + request.getSettlementRunId());

        int lineNo = 1;
        voucher.addLine(line(expense.getId(), gross, 0, "CC settlement expense", lineNo++));
        if (net > 0.009) {
            voucher.addLine(line(creditors.getId(), 0, net, "Payable to collection centres", lineNo++));
        }
        if (tds > 0.009) {
            voucher.addLine(line(tdsPayable.getId(), 0, tds, "TDS on CC settlement", lineNo++));
        }
        voucher.setTotalDebit(gross);
        voucher.setTotalCredit(round2(net + tds));

        LedgerVoucher saved = voucherRepository.save(voucher);
        return voucherRepository
                .findDetailedByIdAndTenantIdAndShopId(saved.getId(), tenantId, shopId)
                .orElse(saved);
    }

    private void ensureAccount(Long tenantId, String shopId, String code, String name, String type) {
        if (accountRepository.existsByTenantIdAndShopIdAndCode(tenantId, shopId, code)) {
            return;
        }
        LedgerAccount account = new LedgerAccount();
        account.setTenantId(tenantId);
        account.setShopId(shopId);
        account.setCode(code);
        account.setName(name);
        account.setAccountType(type);
        account.setActive(Boolean.TRUE);
        account.setSystemAccount(Boolean.TRUE);
        accountRepository.save(account);
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
        line.setDebit(debit);
        line.setCredit(credit);
        line.setLineNarration(narration);
        line.setLineNo(lineNo);
        return line;
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

    private static double safe(Double v) {
        return v == null || v.isNaN() || v.isInfinite() ? 0 : v;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
