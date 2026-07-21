package com.shopmanagement.ledgerservice.dto;

import java.time.LocalDate;

/** Trade sales return / credit note payload for auto journal voucher. */
public class SalesReturnVoucherRequest {
    private Long salesReturnId;
    private String returnNumber;
    private String creditNoteNumber;
    private Long invoiceId;
    private String invoiceNumber;
    private LocalDate returnDate;
    private Double totalAmount;
    private String narration;

    public Long getSalesReturnId() { return salesReturnId; }
    public void setSalesReturnId(Long salesReturnId) { this.salesReturnId = salesReturnId; }
    public String getReturnNumber() { return returnNumber; }
    public void setReturnNumber(String returnNumber) { this.returnNumber = returnNumber; }
    public String getCreditNoteNumber() { return creditNoteNumber; }
    public void setCreditNoteNumber(String creditNoteNumber) { this.creditNoteNumber = creditNoteNumber; }
    public Long getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Long invoiceId) { this.invoiceId = invoiceId; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
    public LocalDate getReturnDate() { return returnDate; }
    public void setReturnDate(LocalDate returnDate) { this.returnDate = returnDate; }
    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }
    public String getNarration() { return narration; }
    public void setNarration(String narration) { this.narration = narration; }
}
