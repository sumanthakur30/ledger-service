package com.shopmanagement.ledgerservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.shopmanagement.ledgerservice.dto.ApItcVoucherRequest;
import com.shopmanagement.ledgerservice.filter.RequestIdFilter;
import com.shopmanagement.ledgerservice.model.LedgerAccount;
import com.shopmanagement.ledgerservice.model.LedgerVoucher;
import com.shopmanagement.ledgerservice.repository.LedgerAccountRepository;
import com.shopmanagement.ledgerservice.repository.LedgerVoucherRepository;

class ApItcVoucherServiceTest {

    private LedgerVoucherRepository voucherRepository;
    private LedgerAccountRepository accountRepository;
    private ApItcVoucherService service;

    @BeforeEach
    void setUp() {
        voucherRepository = mock(LedgerVoucherRepository.class);
        accountRepository = mock(LedgerAccountRepository.class);
        service = new ApItcVoucherService(
                voucherRepository, accountRepository, mock(ChartOfAccountsService.class));
        threadLocal("currentTenantId").set(1L);
        threadLocal("currentShopId").set("SHOP-1");
        threadLocal("currentRole").set("SHOP_OWNER");
        threadLocal("currentPermissions").set(List.of("MANAGE_STOCKS"));
    }

    @AfterEach
    void tearDown() {
        threadLocal("currentTenantId").remove();
        threadLocal("currentShopId").remove();
        threadLocal("currentRole").remove();
        threadLocal("currentPermissions").remove();
    }

    @Test
    void secondCallIsIdempotent() {
        LedgerVoucher existing = new LedgerVoucher();
        existing.setId(44L);
        existing.setSourceType(ApItcVoucherService.SOURCE_AP_ITC);
        existing.setSourceId(9L);
        when(voucherRepository.findDetailedBySource(1L, "SHOP-1", ApItcVoucherService.SOURCE_AP_ITC, 9L))
                .thenReturn(Optional.of(existing));

        ApItcVoucherRequest request = request(9L, 18.0, 9.0, 9.0, 0.0);
        LedgerVoucher first = service.postFromApInvoice(request);
        LedgerVoucher second = service.postFromApInvoice(request);

        assertSame(existing, first);
        assertSame(existing, second);
        verify(voucherRepository, never()).save(any());
    }

    @Test
    void postsInputGstAndCreditorsNotStock() {
        when(voucherRepository.findDetailedBySource(
                eq(1L), eq("SHOP-1"), eq(ApItcVoucherService.SOURCE_AP_ITC), eq(3L)))
                .thenReturn(Optional.empty());
        when(accountRepository.findByTenantIdAndShopIdAndCode(1L, "SHOP-1", "2210"))
                .thenReturn(Optional.of(account(10L, "2210")));
        when(accountRepository.findByTenantIdAndShopIdAndCode(1L, "SHOP-1", "2220"))
                .thenReturn(Optional.of(account(11L, "2220")));
        when(accountRepository.findByTenantIdAndShopIdAndCode(1L, "SHOP-1", "2000"))
                .thenReturn(Optional.of(account(20L, "2000")));
        when(voucherRepository.save(any(LedgerVoucher.class))).thenAnswer(inv -> {
            LedgerVoucher v = inv.getArgument(0);
            v.setId(88L);
            return v;
        });
        when(voucherRepository.findDetailedByIdAndTenantIdAndShopId(88L, 1L, "SHOP-1"))
                .thenReturn(Optional.empty());

        LedgerVoucher saved = service.postFromApInvoice(request(3L, 18.0, 9.0, 9.0, 0.0));

        assertEquals(88L, saved.getId());
        assertEquals(ApItcVoucherService.SOURCE_AP_ITC, saved.getSourceType());
        assertEquals(3L, saved.getSourceId());
        assertEquals("JOURNAL", saved.getVoucherType());
        assertEquals(18.0, saved.getTotalDebit(), 0.001);
        assertEquals(18.0, saved.getTotalCredit(), 0.001);
        assertEquals(7L, saved.getBranchId());
        assertEquals(3, saved.getLines().size());
        assertEquals(10L, saved.getLines().get(0).getAccountId());
        assertEquals(9.0, saved.getLines().get(0).getDebit(), 0.001);
        assertEquals(11L, saved.getLines().get(1).getAccountId());
        assertEquals(9.0, saved.getLines().get(1).getDebit(), 0.001);
        assertEquals(20L, saved.getLines().get(2).getAccountId());
        assertEquals(18.0, saved.getLines().get(2).getCredit(), 0.001);
        assertTrueNoStockLine(saved);
    }

    private static void assertTrueNoStockLine(LedgerVoucher saved) {
        boolean stock = saved.getLines().stream().anyMatch(line -> Long.valueOf(12L).equals(line.getAccountId()));
        org.junit.jupiter.api.Assertions.assertFalse(stock);
    }

    private static ApItcVoucherRequest request(Long id, Double tax, Double cgst, Double sgst, Double igst) {
        ApItcVoucherRequest request = new ApItcVoucherRequest();
        request.setApInvoiceId(id);
        request.setInvoiceNumber("SUP-" + id);
        request.setInvoiceDate(LocalDate.of(2026, 8, 16));
        request.setTaxAmount(tax);
        request.setCgstAmount(cgst);
        request.setSgstAmount(sgst);
        request.setIgstAmount(igst);
        request.setBranchId(7L);
        return request;
    }

    private static LedgerAccount account(Long id, String code) {
        LedgerAccount account = new LedgerAccount();
        account.setId(id);
        account.setCode(code);
        return account;
    }

    @SuppressWarnings("unchecked")
    private <T> ThreadLocal<T> threadLocal(String fieldName) {
        return (ThreadLocal<T>) ReflectionTestUtils.getField(RequestIdFilter.class, fieldName);
    }
}
