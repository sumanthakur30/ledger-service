package com.shopmanagement.ledgerservice.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.ledgerservice.dto.CreateFiscalYearRequest;
import com.shopmanagement.ledgerservice.dto.FiscalYearResponse;
import com.shopmanagement.ledgerservice.filter.RequestIdFilter;
import com.shopmanagement.ledgerservice.model.ShopFiscalYear;
import com.shopmanagement.ledgerservice.model.ShopVoucherSequence;
import com.shopmanagement.ledgerservice.repository.ShopFiscalYearRepository;
import com.shopmanagement.ledgerservice.repository.ShopVoucherSequenceRepository;

@Service
public class FiscalYearService {

    private final ShopFiscalYearRepository yearRepository;
    private final ShopVoucherSequenceRepository sequenceRepository;
    private final PeriodLockService periodLockService;
    private final FiscalYearCloseService closeService;

    public FiscalYearService(
            ShopFiscalYearRepository yearRepository,
            ShopVoucherSequenceRepository sequenceRepository,
            PeriodLockService periodLockService,
            FiscalYearCloseService closeService) {
        this.yearRepository = yearRepository;
        this.sequenceRepository = sequenceRepository;
        this.periodLockService = periodLockService;
        this.closeService = closeService;
    }

    @Transactional
    public List<FiscalYearResponse> list() {
        requireManage();
        ensureYearContaining(LocalDate.now());
        return yearRepository
                .findByTenantIdAndShopIdOrderByStartDateDesc(requireTenantId(), requireShopId())
                .stream()
                .map(FiscalYearService::toResponse)
                .toList();
    }

    @Transactional
    public FiscalYearResponse current() {
        requireManage();
        ShopFiscalYear year = ensureYearContaining(LocalDate.now());
        if (year == null) {
            throw new IllegalArgumentException("No financial year covers today — create one");
        }
        return toResponse(year);
    }

    @Transactional(readOnly = true)
    public FiscalYearResponse get(Long id) {
        requireManage();
        return toResponse(requireYear(id));
    }

    @Transactional
    public FiscalYearResponse create(CreateFiscalYearRequest request) {
        requireManage();
        if (request == null || request.getStartDate() == null || request.getEndDate() == null) {
            throw new IllegalArgumentException("startDate and endDate are required");
        }
        LocalDate start = request.getStartDate();
        LocalDate end = request.getEndDate();
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("endDate must be on or after startDate");
        }
        Long tenantId = requireTenantId();
        String shopId = requireShopId();
        if (yearRepository.countOverlapping(tenantId, shopId, start, end, -1L) > 0) {
            throw new IllegalArgumentException("Fiscal year overlaps an existing year for this shop");
        }
        String code = request.getCode() != null && !request.getCode().isBlank()
                ? request.getCode().trim()
                : FiscalYearMath.codeFor(start, end);
        if (yearRepository.findByTenantIdAndShopIdAndCode(tenantId, shopId, code).isPresent()) {
            throw new IllegalArgumentException("Fiscal year code already exists: " + code);
        }
        ShopFiscalYear year = new ShopFiscalYear();
        year.setTenantId(tenantId);
        year.setShopId(shopId);
        year.setCode(code);
        year.setName(request.getName() != null && !request.getName().isBlank()
                ? request.getName().trim()
                : FiscalYearMath.displayName(code));
        year.setStartDate(start);
        year.setEndDate(end);
        year.setStatus("OPEN");
        year.setOpeningTransferred(Boolean.FALSE);
        return toResponse(yearRepository.save(year));
    }

    @Transactional
    public FiscalYearResponse close(Long id) {
        requireManage();
        ShopFiscalYear year = requireYear(id);
        if (year.isClosed()) {
            return toResponse(year);
        }
        closeService.closeYear(year);
        LocalDate lockedThrough = periodLockService.getLockedThrough();
        if (lockedThrough == null || lockedThrough.isBefore(year.getEndDate())) {
            periodLockService.setLockedThrough(year.getEndDate());
        }
        ensureNextYear(year);
        return toResponse(requireYear(id));
    }

    /** Reject posting into a CLOSED financial year. Missing FY is allowed (legacy shops). */
    @Transactional(readOnly = true)
    public void assertNotClosed(LocalDate voucherDate) {
        LocalDate date = voucherDate != null ? voucherDate : LocalDate.now();
        Long tenantId = RequestIdFilter.getCurrentTenantId();
        String shopId = RequestIdFilter.getCurrentShopId();
        if (tenantId == null || shopId == null || shopId.isBlank()) {
            return;
        }
        yearRepository.findCovering(tenantId, shopId, date).ifPresent(year -> {
            if (year.isClosed()) {
                throw new IllegalArgumentException(
                        "Financial year " + year.getCode() + " is closed — cannot post voucher dated " + date);
            }
        });
    }

    @Transactional
    public String nextVoucherNumber(String voucherType, LocalDate voucherDate) {
        LocalDate date = voucherDate != null ? voucherDate : LocalDate.now();
        String type = voucherType != null && !voucherType.isBlank()
                ? voucherType.trim().toUpperCase(Locale.ROOT)
                : "JOURNAL";
        Optional<ShopFiscalYear> covering = yearRepository.findCovering(requireTenantId(), requireShopId(), date);
        ShopFiscalYear year = covering.orElseGet(() -> ensureYearContaining(date));
        if (year == null) {
            return shortPrefix(type) + "-" + System.currentTimeMillis();
        }
        ShopVoucherSequence sequence = sequenceRepository
                .findForUpdate(year.getTenantId(), year.getShopId(), year.getId(), type)
                .orElseGet(() -> {
                    ShopVoucherSequence created = new ShopVoucherSequence();
                    created.setTenantId(year.getTenantId());
                    created.setShopId(year.getShopId());
                    created.setFiscalYearId(year.getId());
                    created.setVoucherType(type);
                    created.setNextNumber(1);
                    return sequenceRepository.saveAndFlush(created);
                });
        int number = sequence.getNextNumber() == null ? 1 : sequence.getNextNumber();
        sequence.setNextNumber(number + 1);
        sequenceRepository.save(sequence);
        return year.getCode() + "/" + shortPrefix(type) + "/" + String.format(Locale.ROOT, "%04d", number);
    }

    @Transactional(readOnly = true)
    public Optional<ShopFiscalYear> findCovering(LocalDate asOf) {
        Long tenantId = RequestIdFilter.getCurrentTenantId();
        String shopId = RequestIdFilter.getCurrentShopId();
        if (tenantId == null || shopId == null || shopId.isBlank()) {
            return Optional.empty();
        }
        return yearRepository.findCovering(tenantId, shopId, asOf != null ? asOf : LocalDate.now());
    }

    private ShopFiscalYear ensureYearContaining(LocalDate asOf) {
        Long tenantId = requireTenantId();
        String shopId = requireShopId();
        LocalDate date = asOf != null ? asOf : LocalDate.now();
        Optional<ShopFiscalYear> existing = yearRepository.findCovering(tenantId, shopId, date);
        if (existing.isPresent()) {
            return existing.get();
        }
        FiscalYearMath.Range range = FiscalYearMath.indianRangeContaining(date);
        if (yearRepository.countOverlapping(tenantId, shopId, range.start(), range.end(), -1L) > 0) {
            return null;
        }
        String code = FiscalYearMath.codeFor(range.start(), range.end());
        Optional<ShopFiscalYear> byCode = yearRepository.findByTenantIdAndShopIdAndCode(tenantId, shopId, code);
        if (byCode.isPresent()) {
            return byCode.get();
        }
        ShopFiscalYear year = new ShopFiscalYear();
        year.setTenantId(tenantId);
        year.setShopId(shopId);
        year.setCode(code);
        year.setName(FiscalYearMath.displayName(code));
        year.setStartDate(range.start());
        year.setEndDate(range.end());
        year.setStatus("OPEN");
        year.setOpeningTransferred(Boolean.FALSE);
        return yearRepository.save(year);
    }

    private void ensureNextYear(ShopFiscalYear closed) {
        FiscalYearMath.Range next = FiscalYearMath.nextRange(closed.getStartDate(), closed.getEndDate());
        Long tenantId = closed.getTenantId();
        String shopId = closed.getShopId();
        if (yearRepository.findCovering(tenantId, shopId, next.start()).isPresent()) {
            return;
        }
        if (yearRepository.countOverlapping(tenantId, shopId, next.start(), next.end(), -1L) > 0) {
            return;
        }
        String code = FiscalYearMath.codeFor(next.start(), next.end());
        if (yearRepository.findByTenantIdAndShopIdAndCode(tenantId, shopId, code).isPresent()) {
            return;
        }
        ShopFiscalYear year = new ShopFiscalYear();
        year.setTenantId(tenantId);
        year.setShopId(shopId);
        year.setCode(code);
        year.setName(FiscalYearMath.displayName(code));
        year.setStartDate(next.start());
        year.setEndDate(next.end());
        year.setStatus("OPEN");
        year.setOpeningTransferred(Boolean.FALSE);
        yearRepository.save(year);
    }

    private ShopFiscalYear requireYear(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("fiscal year id is required");
        }
        return yearRepository
                .findByIdAndTenantIdAndShopId(id, requireTenantId(), requireShopId())
                .orElseThrow(() -> new IllegalArgumentException("Fiscal year not found: " + id));
    }

    static FiscalYearResponse toResponse(ShopFiscalYear year) {
        return new FiscalYearResponse(
                year.getId(),
                year.getCode(),
                year.getName(),
                year.getStartDate(),
                year.getEndDate(),
                year.getStatus(),
                Boolean.TRUE.equals(year.getOpeningTransferred()),
                year.getCloseVoucherId(),
                year.getClosedAt(),
                year.getClosedBy());
    }

    static String shortPrefix(String voucherType) {
        return switch (voucherType) {
            case "JOURNAL" -> "JV";
            case "SALES" -> "SL";
            case "PAYMENT" -> "PY";
            case "RECEIPT" -> "RC";
            case "YEAR_END" -> "YE";
            default -> voucherType.length() <= 3 ? voucherType : voucherType.substring(0, 3);
        };
    }

    private void requireManage() {
        String role = RequestIdFilter.getCurrentRole();
        if ("SUPER_ADMIN".equals(role) || "SHOP_OWNER".equals(role) || "TRADE_ACCOUNTANT".equals(role)) {
            return;
        }
        var perms = RequestIdFilter.getCurrentPermissions();
        if (perms.contains("MANAGE_ORDERS")
                || perms.contains("MANAGE_FINANCE")
                || perms.contains("PROCUREMENT_FINANCE")) {
            return;
        }
        throw new SecurityException("Forbidden: missing permission to manage financial years");
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
}
