package com.shopmanagement.ledgerservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.shopmanagement.ledgerservice.service.BankStatementCsvParser.ParseResult;
import com.shopmanagement.ledgerservice.service.BankStatementCsvParser.ParsedLine;

class BankStatementCsvParserTest {

    @Test
    void parsesHdfcStyleHeaders() {
        String csv = """
                Date,Narration,Chq./Ref.No.,Value Dt,Withdrawal Amt.,Deposit Amt.,Closing Balance
                01/08/2026,UPI-SHOP-AA,000012345678,01/08/2026,1500.00,,23500.00
                02/08/2026,NEFT-CUST,,02/08/2026,,2000.50,25500.50
                """;
        ParseResult result = BankStatementCsvParser.parse("hdfc.csv", csv.getBytes(StandardCharsets.UTF_8));
        assertEquals(2, result.lines().size());
        ParsedLine out = result.lines().get(0);
        assertEquals(LocalDate.of(2026, 8, 1), out.txnDate());
        assertEquals("UPI-SHOP-AA", out.description());
        assertEquals("000012345678", out.reference());
        assertEquals(1500.00, out.debit());
        assertEquals(0.0, out.credit());
        assertEquals(23500.00, out.balance());
        ParsedLine in = result.lines().get(1);
        assertEquals(2000.50, in.credit());
        assertEquals(0.0, in.debit());
    }

    @Test
    void parsesSbiStyleAndIndianDates() {
        String csv = """
                Txn Date,Value Date,Description,Ref No./Cheque No.,Debit,Credit,Balance
                1 Aug 2026,1 Aug 2026,UPI/foo,CHQ9911,1500.00,,25000.00
                02-Aug-2026,02-Aug-2026,IMPS in,,,"3,000.00",28000.00
                """;
        ParseResult result = BankStatementCsvParser.parseCsv(csv);
        assertEquals(2, result.lines().size());
        assertEquals(LocalDate.of(2026, 8, 1), result.lines().get(0).txnDate());
        assertEquals("CHQ9911", result.lines().get(0).reference());
        assertEquals(3000.00, result.lines().get(1).credit());
    }

    @Test
    void parsesSingleAmountWithDrCr() {
        String csv = """
                Date,Description,Amount,Dr / Cr,Balance
                16/08/2026,UPI out,"1,500.00",Dr,25000
                16/08/2026,Cash in,800,Cr,25800
                """;
        ParseResult result = BankStatementCsvParser.parseCsv(csv);
        assertEquals(2, result.lines().size());
        assertEquals(1500.00, result.lines().get(0).debit());
        assertEquals(0.0, result.lines().get(0).credit());
        assertEquals(800.00, result.lines().get(1).credit());
    }

    @Test
    void parsesQuotedCommaAndSkipsOpeningBalance() {
        String csv = """
                date,description,debit,credit,amount,balance,cheque
                2026-08-01,Opening balance,,,,10000,
                2026-08-02,"UPI, vendor",250.25,,,9750,REF-9
                """;
        ParseResult result = BankStatementCsvParser.parseCsv(csv);
        assertEquals(1, result.lines().size());
        assertEquals(1, result.skipped());
        assertEquals("UPI, vendor", result.lines().get(0).description());
        assertEquals(250.25, result.lines().get(0).debit());
        assertEquals("REF-9", result.lines().get(0).reference());
    }

    @Test
    void skipsBankMetadataRowsBeforeHeader() {
        String csv = """
                Account Name,Current A/c
                Account Number,123456
                Date,Narration,Debit,Credit,Balance
                15-08-2026,POS collection, ,500.00,10500
                """;
        ParseResult result = BankStatementCsvParser.parseCsv(csv);
        assertEquals(1, result.lines().size());
        assertEquals(LocalDate.of(2026, 8, 15), result.lines().get(0).txnDate());
        assertEquals(500.00, result.lines().get(0).credit());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("metadata")));
    }

    @Test
    void stripsBomAndParsesIsoDate() {
        String csv = "\uFEFFDate,Description,Debit,Credit\n2026-08-16,Cash sale,,100.00\n";
        ParseResult result = BankStatementCsvParser.parse("stmt.csv", csv.getBytes(StandardCharsets.UTF_8));
        assertEquals(1, result.lines().size());
        assertEquals(100.00, result.lines().get(0).credit());
        assertNull(result.lines().get(0).balance());
    }

    @Test
    void rejectsExcelExtension() {
        assertThrows(IllegalArgumentException.class,
                () -> BankStatementCsvParser.parse("stmt.xlsx", "x".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsMissingHeader() {
        assertThrows(IllegalArgumentException.class, () -> BankStatementCsvParser.parseCsv("foo,bar\n1,2\n"));
    }
}
