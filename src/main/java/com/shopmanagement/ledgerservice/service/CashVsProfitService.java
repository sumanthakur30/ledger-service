package com.shopmanagement.ledgerservice.service;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopmanagement.ledgerservice.dto.AccountBookResponse;
import com.shopmanagement.ledgerservice.dto.CashVsProfitResponse;
import com.shopmanagement.ledgerservice.dto.ProfitAndLossResponse;

/**
 * Paired cash movement (Cash 1000 + Bank 1010) vs accrual P&amp;L net profit.
 * Does not invent a second P&amp;L engine — reuses FinalAccounts + cash book.
 */
@Service
public class CashVsProfitService {

    private static final String NOTE =
            "Cash ≠ Profit. Cash movement sums debits/credits on Cash (1000) and Bank (1010). "
                    + "Accrual net profit is from posted INCOME − EXPENSE (includes credit sales, unpaid expenses, stock).";

    private final AccountBookService accountBookService;
    private final FinalAccountsService finalAccountsService;
    private final ChartOfAccountsService chartOfAccountsService;

    public CashVsProfitService(
            AccountBookService accountBookService,
            FinalAccountsService finalAccountsService,
            ChartOfAccountsService chartOfAccountsService) {
        this.accountBookService = accountBookService;
        this.finalAccountsService = finalAccountsService;
        this.chartOfAccountsService = chartOfAccountsService;
    }

    @Transactional
    public CashVsProfitResponse cashVsProfit(LocalDate from, LocalDate to) {
        LocalDate fromDate = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate toDate = to != null ? to : LocalDate.now();
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("from must be on or before to");
        }

        chartOfAccountsService.seedDefaults();
        AccountBookResponse cash = accountBookService.book("1000", fromDate, toDate);
        AccountBookResponse bank = accountBookService.book("1010", fromDate, toDate);
        double cashIn = round2(cash.periodDebit() + bank.periodDebit());
        double cashOut = round2(cash.periodCredit() + bank.periodCredit());
        double netCash = round2(cashIn - cashOut);

        ProfitAndLossResponse pnl = finalAccountsService.profitAndLoss(fromDate, toDate);
        return new CashVsProfitResponse(
                fromDate,
                toDate,
                cashIn,
                cashOut,
                netCash,
                pnl.netProfit(),
                NOTE);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
