package com.shopmanagement.ledgerservice.web;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.shopmanagement.ledgerservice.dto.BankReconDtos.BatchSummary;
import com.shopmanagement.ledgerservice.dto.BankReconDtos.MatchResponse;
import com.shopmanagement.ledgerservice.service.BankStatementImportService;

/**
 * Bank / cash statement CSV import on existing cash-book recon ticks. Not a live bank API.
 */
@RestController
@RequestMapping("/api/v1/ledger/bank-recon")
public class BankReconController {

    private final BankStatementImportService importService;

    public BankReconController(BankStatementImportService importService) {
        this.importService = importService;
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MatchResponse importCsv(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "1010") String accountCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return importService.importCsv(file, accountCode, from, to);
    }

    @GetMapping("/batches")
    public List<BatchSummary> listBatches(@RequestParam(required = false) String accountCode) {
        return importService.listBatches(accountCode);
    }

    @GetMapping("/batches/{id:\\d+}")
    public MatchResponse getBatch(@PathVariable Long id) {
        return importService.getBatch(id);
    }

    @PostMapping("/batches/{id:\\d+}/confirm-matched")
    public MatchResponse confirmMatched(@PathVariable Long id) {
        return importService.confirmMatched(id);
    }
}
