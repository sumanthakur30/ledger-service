package com.shopmanagement.ledgerservice.dto;

public class ExpenseCategoryRequest {
    private String code;
    private String name;
    private Long parentId;
    private Long ledgerAccountId;
    private Integer sortOrder;
    private Boolean active;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Long getLedgerAccountId() { return ledgerAccountId; }
    public void setLedgerAccountId(Long ledgerAccountId) { this.ledgerAccountId = ledgerAccountId; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
