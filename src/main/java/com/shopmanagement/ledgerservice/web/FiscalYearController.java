package com.shopmanagement.ledgerservice.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.ledgerservice.dto.CreateFiscalYearRequest;
import com.shopmanagement.ledgerservice.dto.FiscalYearResponse;
import com.shopmanagement.ledgerservice.service.FiscalYearService;

@RestController
@RequestMapping("/api/v1/ledger/fiscal-years")
public class FiscalYearController {

    private final FiscalYearService fiscalYearService;

    public FiscalYearController(FiscalYearService fiscalYearService) {
        this.fiscalYearService = fiscalYearService;
    }

    @GetMapping
    public List<FiscalYearResponse> list() {
        return fiscalYearService.list();
    }

    @GetMapping("/current")
    public FiscalYearResponse current() {
        return fiscalYearService.current();
    }

    @GetMapping("/{id:\\d+}")
    public FiscalYearResponse get(@PathVariable Long id) {
        return fiscalYearService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FiscalYearResponse create(@RequestBody CreateFiscalYearRequest request) {
        return fiscalYearService.create(request);
    }

    @PostMapping("/{id:\\d+}/close")
    public FiscalYearResponse close(@PathVariable Long id) {
        return fiscalYearService.close(id);
    }
}
