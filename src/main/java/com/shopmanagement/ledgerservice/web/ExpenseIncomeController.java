package com.shopmanagement.ledgerservice.web;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.ledgerservice.dto.ExpenseCategoryRequest;
import com.shopmanagement.ledgerservice.dto.ExpenseEntryRequest;
import com.shopmanagement.ledgerservice.dto.OtherIncomeRequest;
import com.shopmanagement.ledgerservice.model.ExpenseCategory;
import com.shopmanagement.ledgerservice.model.ExpenseEntry;
import com.shopmanagement.ledgerservice.model.IncomeType;
import com.shopmanagement.ledgerservice.model.OtherIncomeEntry;
import com.shopmanagement.ledgerservice.service.ExpenseCategoryService;
import com.shopmanagement.ledgerservice.service.ExpenseEntryService;
import com.shopmanagement.ledgerservice.service.OtherIncomeService;

/**
 * Expense / other-income operational APIs under the ledger base path.
 * Exception handling is shared via {@link LedgerController}.
 */
@RestController
@RequestMapping("/api/v1/ledger")
public class ExpenseIncomeController {

    private final ExpenseCategoryService categoryService;
    private final ExpenseEntryService expenseEntryService;
    private final OtherIncomeService otherIncomeService;

    public ExpenseIncomeController(
            ExpenseCategoryService categoryService,
            ExpenseEntryService expenseEntryService,
            OtherIncomeService otherIncomeService) {
        this.categoryService = categoryService;
        this.expenseEntryService = expenseEntryService;
        this.otherIncomeService = otherIncomeService;
    }

    @GetMapping("/expense-categories")
    public List<ExpenseCategory> listCategories() {
        return categoryService.listCategories();
    }

    @PostMapping("/expense-categories")
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseCategory createCategory(@RequestBody ExpenseCategoryRequest request) {
        return categoryService.createCategory(request);
    }

    @PutMapping("/expense-categories/{id:\\d+}")
    public ExpenseCategory updateCategory(@PathVariable Long id, @RequestBody ExpenseCategoryRequest request) {
        return categoryService.updateCategory(id, request);
    }

    @PostMapping("/expense-categories/seed-defaults")
    public List<ExpenseCategory> seedCategories() {
        return categoryService.seedDefaultCategories();
    }

    @GetMapping("/income-types")
    public List<IncomeType> listIncomeTypes() {
        return categoryService.listIncomeTypes();
    }

    @PostMapping("/income-types/seed-defaults")
    public List<IncomeType> seedIncomeTypes() {
        return categoryService.seedDefaultIncomeTypes();
    }

    @GetMapping("/expenses")
    public List<ExpenseEntry> listExpenses(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long categoryId) {
        return expenseEntryService.list(from, to, status, categoryId);
    }

    @GetMapping("/expenses/{id:\\d+}")
    public ExpenseEntry getExpense(@PathVariable Long id) {
        return expenseEntryService.get(id);
    }

    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseEntry createExpense(@RequestBody ExpenseEntryRequest request) {
        return expenseEntryService.create(request);
    }

    @PutMapping("/expenses/{id:\\d+}")
    public ExpenseEntry updateExpense(@PathVariable Long id, @RequestBody ExpenseEntryRequest request) {
        return expenseEntryService.update(id, request);
    }

    @PostMapping("/expenses/{id:\\d+}/submit")
    public ExpenseEntry submitExpense(@PathVariable Long id) {
        return expenseEntryService.submit(id);
    }

    @PostMapping("/expenses/{id:\\d+}/approve")
    public ExpenseEntry approveExpense(@PathVariable Long id) {
        return expenseEntryService.approve(id);
    }

    @PostMapping("/expenses/{id:\\d+}/post")
    public ExpenseEntry postExpense(@PathVariable Long id) {
        return expenseEntryService.post(id);
    }

    @PostMapping("/expenses/{id:\\d+}/void")
    public ExpenseEntry voidExpense(@PathVariable Long id) {
        return expenseEntryService.voidEntry(id);
    }

    @GetMapping("/other-incomes")
    public List<OtherIncomeEntry> listOtherIncomes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String status) {
        return otherIncomeService.list(from, to, status);
    }

    @GetMapping("/other-incomes/{id:\\d+}")
    public OtherIncomeEntry getOtherIncome(@PathVariable Long id) {
        return otherIncomeService.get(id);
    }

    @PostMapping("/other-incomes")
    @ResponseStatus(HttpStatus.CREATED)
    public OtherIncomeEntry createOtherIncome(@RequestBody OtherIncomeRequest request) {
        return otherIncomeService.create(request);
    }

    @PutMapping("/other-incomes/{id:\\d+}")
    public OtherIncomeEntry updateOtherIncome(@PathVariable Long id, @RequestBody OtherIncomeRequest request) {
        return otherIncomeService.update(id, request);
    }

    @PostMapping("/other-incomes/{id:\\d+}/post")
    public OtherIncomeEntry postOtherIncome(@PathVariable Long id) {
        return otherIncomeService.post(id);
    }

    @PostMapping("/other-incomes/{id:\\d+}/void")
    public OtherIncomeEntry voidOtherIncome(@PathVariable Long id) {
        return otherIncomeService.voidEntry(id);
    }
}
