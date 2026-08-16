package com.shopmanagement.ledgerservice.dto;

import java.time.LocalDate;

public class CreateFiscalYearRequest {
    private String code;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
}
