package com.shopmanagement.ledgerservice.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.shopmanagement.ledgerservice.dto.TrialBalanceRow;

/**
 * Pure trial-balance accumulation. Consolidated (filter null) includes every voucher;
 * a branch filter includes only that {@code branch_id} (null-branch vouchers stay in consolidated).
 */
public final class TrialBalanceMath {

    private TrialBalanceMath() {
    }

    public record AccountSeed(Long id, String code, String name, String accountType) {
    }

    public record Line(Long accountId, double debit, double credit) {
    }

    public record PostedVoucher(
            Long branchId,
            String status,
            String voucherType,
            String sourceType,
            List<Line> lines) {
    }

    /** Treat missing / non-positive ids as unassigned (do not invent a branch). */
    public static Long normalize(Long branchId) {
        if (branchId == null || branchId <= 0) {
            return null;
        }
        return branchId;
    }

    public static boolean includeVoucher(Long voucherBranchId, Long filterBranchId) {
        if (filterBranchId == null) {
            return true;
        }
        return filterBranchId.equals(voucherBranchId);
    }

    public static List<TrialBalanceRow> accumulate(
            List<AccountSeed> accounts,
            List<PostedVoucher> vouchers,
            Long filterBranchId,
            boolean excludeYearEnd) {
        Map<Long, TrialBalanceRow> byId = new LinkedHashMap<>();
        if (accounts != null) {
            for (AccountSeed account : accounts) {
                if (account == null || account.id() == null) {
                    continue;
                }
                byId.put(account.id(), new TrialBalanceRow(
                        account.id(), account.code(), account.name(), account.accountType(), 0, 0));
            }
        }
        if (vouchers != null) {
            for (PostedVoucher voucher : vouchers) {
                if (voucher == null || !includePosted(voucher, filterBranchId, excludeYearEnd)) {
                    continue;
                }
                if (voucher.lines() == null) {
                    continue;
                }
                for (Line line : voucher.lines()) {
                    if (line == null || line.accountId() == null) {
                        continue;
                    }
                    TrialBalanceRow existing = byId.get(line.accountId());
                    if (existing == null) {
                        continue;
                    }
                    byId.put(line.accountId(), new TrialBalanceRow(
                            existing.accountId(),
                            existing.code(),
                            existing.name(),
                            existing.accountType(),
                            round2(existing.debit() + safe(line.debit())),
                            round2(existing.credit() + safe(line.credit()))));
                }
            }
        }
        List<TrialBalanceRow> rows = new ArrayList<>();
        for (TrialBalanceRow row : byId.values()) {
            if (row.debit() > 0.009 || row.credit() > 0.009) {
                rows.add(row);
            }
        }
        return rows;
    }

    private static boolean includePosted(PostedVoucher voucher, Long filterBranchId, boolean excludeYearEnd) {
        if (!"POSTED".equalsIgnoreCase(voucher.status())) {
            return false;
        }
        if (!includeVoucher(voucher.branchId(), filterBranchId)) {
            return false;
        }
        if (excludeYearEnd && ("YEAR_END".equalsIgnoreCase(voucher.voucherType())
                || "FY_CLOSE".equalsIgnoreCase(voucher.sourceType()))) {
            return false;
        }
        return true;
    }

    private static double safe(double value) {
        return value;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
