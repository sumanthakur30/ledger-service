package com.shopmanagement.ledgerservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.shopmanagement.ledgerservice.dto.CollectionReceiptVoucherRequest;
import com.shopmanagement.ledgerservice.filter.RequestIdFilter;
import com.shopmanagement.ledgerservice.model.LedgerAccount;
import com.shopmanagement.ledgerservice.model.LedgerVoucher;
import com.shopmanagement.ledgerservice.repository.LedgerAccountRepository;
import com.shopmanagement.ledgerservice.repository.LedgerVoucherRepository;

class CollectionReceiptVoucherServiceTest {

    private LedgerVoucherRepository voucherRepository;
    private LedgerAccountRepository accountRepository;
    private CollectionReceiptVoucherService service;

    @BeforeEach
    void setUp() {
        voucherRepository = mock(LedgerVoucherRepository.class);
        accountRepository = mock(LedgerAccountRepository.class);
        service = new CollectionReceiptVoucherService(
                voucherRepository, accountRepository, mock(ChartOfAccountsService.class));
        threadLocal("currentTenantId").set(1L);
        threadLocal("currentShopId").set("SHOP-1");
        threadLocal("currentRole").set("SHOP_OWNER");
        threadLocal("currentPermissions").set(List.of("MANAGE_ORDERS"));
    }

    @AfterEach
    void tearDown() {
        threadLocal("currentTenantId").remove();
        threadLocal("currentShopId").remove();
        threadLocal("currentRole").remove();
        threadLocal("currentPermissions").remove();
    }

    @Test
    void secondPosCollectionCallIsIdempotent() {
        LedgerVoucher existing = new LedgerVoucher();
        existing.setId(77L);
        existing.setSourceType(CollectionReceiptVoucherService.SOURCE_POS_COLLECTION);
        existing.setSourceId(55L);
        when(voucherRepository.findDetailedBySource(
                1L, "SHOP-1", CollectionReceiptVoucherService.SOURCE_POS_COLLECTION, 55L))
                .thenReturn(Optional.of(existing));

        CollectionReceiptVoucherRequest request = new CollectionReceiptVoucherRequest();
        request.setPaymentId(55L);
        request.setOrderId(9L);
        request.setAmount(60.0);
        request.setPaymentMethod("UPI");

        LedgerVoucher first = service.postFromPosCollection(request);
        LedgerVoucher second = service.postFromPosCollection(request);

        assertSame(existing, first);
        assertSame(existing, second);
        assertEquals(77L, second.getId());
        verify(voucherRepository, never()).save(any());
    }

    @Test
    void posCollectionPostsCashAndDebtorsForThisAmount() {
        when(voucherRepository.findDetailedBySource(
                eq(1L), eq("SHOP-1"), eq(CollectionReceiptVoucherService.SOURCE_POS_COLLECTION), eq(12L)))
                .thenReturn(Optional.empty());
        when(accountRepository.findByTenantIdAndShopIdAndCode(1L, "SHOP-1", "1000"))
                .thenReturn(Optional.of(account(1L, "1000")));
        when(accountRepository.findByTenantIdAndShopIdAndCode(1L, "SHOP-1", "1100"))
                .thenReturn(Optional.of(account(2L, "1100")));
        when(voucherRepository.save(any(LedgerVoucher.class))).thenAnswer(inv -> {
            LedgerVoucher v = inv.getArgument(0);
            v.setId(99L);
            return v;
        });
        when(voucherRepository.findDetailedByIdAndTenantIdAndShopId(99L, 1L, "SHOP-1"))
                .thenReturn(Optional.empty());

        CollectionReceiptVoucherRequest request = new CollectionReceiptVoucherRequest();
        request.setPaymentId(12L);
        request.setOrderId(3L);
        request.setOrderNumber("POS-3");
        request.setAmount(40.0);
        request.setPaymentMethod("CASH");
        request.setBranchId(8L);

        LedgerVoucher saved = service.postFromPosCollection(request);

        assertEquals(99L, saved.getId());
        assertEquals(CollectionReceiptVoucherService.SOURCE_POS_COLLECTION, saved.getSourceType());
        assertEquals(12L, saved.getSourceId());
        assertEquals("RECEIPT", saved.getVoucherType());
        assertEquals(40.0, saved.getTotalDebit(), 0.001);
        assertEquals(40.0, saved.getTotalCredit(), 0.001);
        assertEquals(8L, saved.getBranchId());
        assertEquals(2, saved.getLines().size());
        assertEquals(40.0, saved.getLines().get(0).getDebit(), 0.001);
        assertEquals(40.0, saved.getLines().get(1).getCredit(), 0.001);
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
