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

import com.shopmanagement.ledgerservice.dto.PurchaseDebitNoteVoucherRequest;
import com.shopmanagement.ledgerservice.filter.RequestIdFilter;
import com.shopmanagement.ledgerservice.model.LedgerAccount;
import com.shopmanagement.ledgerservice.model.LedgerVoucher;
import com.shopmanagement.ledgerservice.repository.LedgerAccountRepository;
import com.shopmanagement.ledgerservice.repository.LedgerVoucherRepository;

class PurchaseDebitNoteVoucherServiceTest {

    private LedgerVoucherRepository voucherRepository;
    private LedgerAccountRepository accountRepository;
    private PurchaseDebitNoteVoucherService service;

    @BeforeEach
    void setUp() {
        voucherRepository = mock(LedgerVoucherRepository.class);
        accountRepository = mock(LedgerAccountRepository.class);
        service = new PurchaseDebitNoteVoucherService(
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
        existing.setId(55L);
        existing.setSourceType(PurchaseDebitNoteVoucherService.SOURCE_PURCHASE_DEBIT_NOTE);
        existing.setSourceId(4L);
        when(voucherRepository.findDetailedBySource(
                1L, "SHOP-1", PurchaseDebitNoteVoucherService.SOURCE_PURCHASE_DEBIT_NOTE, 4L))
                .thenReturn(Optional.of(existing));

        PurchaseDebitNoteVoucherRequest request = request(4L, 100.0, 18.0, 9.0, 9.0, 0.0, true);
        LedgerVoucher first = service.postFromPurchaseReturn(request);
        LedgerVoucher second = service.postFromPurchaseReturn(request);

        assertSame(existing, first);
        assertSame(existing, second);
        verify(voucherRepository, never()).save(any());
    }

    @Test
    void postsCreditorsInputGstAndStock() {
        when(voucherRepository.findDetailedBySource(
                eq(1L), eq("SHOP-1"), eq(PurchaseDebitNoteVoucherService.SOURCE_PURCHASE_DEBIT_NOTE), eq(8L)))
                .thenReturn(Optional.empty());
        when(accountRepository.findByTenantIdAndShopIdAndCode(1L, "SHOP-1", "2000"))
                .thenReturn(Optional.of(account(20L, "2000")));
        when(accountRepository.findByTenantIdAndShopIdAndCode(1L, "SHOP-1", "2210"))
                .thenReturn(Optional.of(account(10L, "2210")));
        when(accountRepository.findByTenantIdAndShopIdAndCode(1L, "SHOP-1", "2220"))
                .thenReturn(Optional.of(account(11L, "2220")));
        when(accountRepository.findByTenantIdAndShopIdAndCode(1L, "SHOP-1", "1200"))
                .thenReturn(Optional.of(account(12L, "1200")));
        when(voucherRepository.save(any(LedgerVoucher.class))).thenAnswer(inv -> {
            LedgerVoucher v = inv.getArgument(0);
            v.setId(99L);
            return v;
        });
        when(voucherRepository.findDetailedByIdAndTenantIdAndShopId(99L, 1L, "SHOP-1"))
                .thenReturn(Optional.empty());

        LedgerVoucher saved = service.postFromPurchaseReturn(request(8L, 100.0, 18.0, 9.0, 9.0, 0.0, true));

        assertEquals(99L, saved.getId());
        assertEquals(PurchaseDebitNoteVoucherService.SOURCE_PURCHASE_DEBIT_NOTE, saved.getSourceType());
        assertEquals(8L, saved.getSourceId());
        assertEquals("DEBIT_NOTE", saved.getVoucherType());
        assertEquals(118.0, saved.getTotalDebit(), 0.001);
        assertEquals(118.0, saved.getTotalCredit(), 0.001);
        assertEquals(7L, saved.getBranchId());
        assertEquals(4, saved.getLines().size());
        assertEquals(20L, saved.getLines().get(0).getAccountId());
        assertEquals(118.0, saved.getLines().get(0).getDebit(), 0.001);
        assertEquals(10L, saved.getLines().get(1).getAccountId());
        assertEquals(9.0, saved.getLines().get(1).getCredit(), 0.001);
        assertEquals(11L, saved.getLines().get(2).getAccountId());
        assertEquals(9.0, saved.getLines().get(2).getCredit(), 0.001);
        assertEquals(12L, saved.getLines().get(3).getAccountId());
        assertEquals(100.0, saved.getLines().get(3).getCredit(), 0.001);
    }

    @Test
    void creditStockFalseOmitsStockLine() {
        when(voucherRepository.findDetailedBySource(
                eq(1L), eq("SHOP-1"), eq(PurchaseDebitNoteVoucherService.SOURCE_PURCHASE_DEBIT_NOTE), eq(2L)))
                .thenReturn(Optional.empty());
        when(accountRepository.findByTenantIdAndShopIdAndCode(1L, "SHOP-1", "2000"))
                .thenReturn(Optional.of(account(20L, "2000")));
        when(accountRepository.findByTenantIdAndShopIdAndCode(1L, "SHOP-1", "2230"))
                .thenReturn(Optional.of(account(13L, "2230")));
        when(voucherRepository.save(any(LedgerVoucher.class))).thenAnswer(inv -> {
            LedgerVoucher v = inv.getArgument(0);
            v.setId(77L);
            return v;
        });
        when(voucherRepository.findDetailedByIdAndTenantIdAndShopId(77L, 1L, "SHOP-1"))
                .thenReturn(Optional.empty());

        LedgerVoucher saved = service.postFromPurchaseReturn(request(2L, 100.0, 18.0, 0.0, 0.0, 18.0, false));

        assertEquals(18.0, saved.getTotalDebit(), 0.001);
        assertEquals(2, saved.getLines().size());
        assertEquals(20L, saved.getLines().get(0).getAccountId());
        assertEquals(18.0, saved.getLines().get(0).getDebit(), 0.001);
        assertEquals(13L, saved.getLines().get(1).getAccountId());
        assertEquals(18.0, saved.getLines().get(1).getCredit(), 0.001);
        boolean stock = saved.getLines().stream().anyMatch(line -> Long.valueOf(12L).equals(line.getAccountId()));
        org.junit.jupiter.api.Assertions.assertFalse(stock);
    }

    private static PurchaseDebitNoteVoucherRequest request(
            Long id, Double stock, Double tax, Double cgst, Double sgst, Double igst, boolean creditStock) {
        PurchaseDebitNoteVoucherRequest request = new PurchaseDebitNoteVoucherRequest();
        request.setPurchaseReturnId(id);
        request.setReturnNumber("PRET-" + id);
        request.setReturnDate(LocalDate.of(2026, 8, 16));
        request.setStockAmount(stock);
        request.setTaxAmount(tax);
        request.setCgstAmount(cgst);
        request.setSgstAmount(sgst);
        request.setIgstAmount(igst);
        request.setCreditStock(creditStock);
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
