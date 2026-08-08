package com.shopmanagement.ledgerservice.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopmanagement.ledgerservice.config.ExpenseModuleProperties;
import com.shopmanagement.ledgerservice.dto.ExpenseCategoryRequest;
import com.shopmanagement.ledgerservice.model.ExpenseCategory;
import com.shopmanagement.ledgerservice.model.IncomeType;
import com.shopmanagement.ledgerservice.model.LedgerAccount;
import com.shopmanagement.ledgerservice.repository.ExpenseCategoryRepository;
import com.shopmanagement.ledgerservice.repository.IncomeTypeRepository;
import com.shopmanagement.ledgerservice.repository.LedgerAccountRepository;

/**
 * Expense category + income type masters. Seed templates loaded from classpath JSON
 * (not hardcoded enums in business posting logic).
 */
@Service
public class ExpenseCategoryService {

    private static final String TEMPLATE_PATH = "expense-templates/default-categories.json";

    private final ExpenseCategoryRepository categoryRepository;
    private final IncomeTypeRepository incomeTypeRepository;
    private final LedgerAccountRepository accountRepository;
    private final ChartOfAccountsService chartOfAccountsService;
    private final ExpenseModuleProperties properties;
    private final ObjectMapper objectMapper;

    public ExpenseCategoryService(
            ExpenseCategoryRepository categoryRepository,
            IncomeTypeRepository incomeTypeRepository,
            LedgerAccountRepository accountRepository,
            ChartOfAccountsService chartOfAccountsService,
            ExpenseModuleProperties properties,
            ObjectMapper objectMapper) {
        this.categoryRepository = categoryRepository;
        this.incomeTypeRepository = incomeTypeRepository;
        this.accountRepository = accountRepository;
        this.chartOfAccountsService = chartOfAccountsService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ExpenseCategory> listCategories() {
        assertEnabled();
        FinanceAccess.requireFinanceAccess();
        return categoryRepository.findByTenantIdAndShopIdOrderBySortOrderAscCodeAsc(
                FinanceAccess.requireTenantId(), FinanceAccess.requireShopId());
    }

    @Transactional
    public ExpenseCategory createCategory(ExpenseCategoryRequest request) {
        assertEnabled();
        FinanceAccess.requireFinanceAccess();
        if (request == null || blank(request.getCode()) || blank(request.getName()) || request.getLedgerAccountId() == null) {
            throw new IllegalArgumentException("code, name and ledgerAccountId are required");
        }
        Long tenantId = FinanceAccess.requireTenantId();
        String shopId = FinanceAccess.requireShopId();
        String code = request.getCode().trim().toUpperCase(Locale.ROOT);
        if (categoryRepository.existsByTenantIdAndShopIdAndCode(tenantId, shopId, code)) {
            throw new IllegalArgumentException("Category code already exists: " + code);
        }
        LedgerAccount account = requireExpenseAccount(tenantId, shopId, request.getLedgerAccountId());
        ExpenseCategory category = new ExpenseCategory();
        category.setTenantId(tenantId);
        category.setShopId(shopId);
        category.setCode(code);
        category.setName(request.getName().trim());
        category.setParentId(request.getParentId());
        category.setLedgerAccountId(account.getId());
        category.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 100);
        category.setActive(request.getActive() == null || Boolean.TRUE.equals(request.getActive()));
        category.setSystemSeed(Boolean.FALSE);
        return categoryRepository.save(category);
    }

    @Transactional
    public ExpenseCategory updateCategory(Long id, ExpenseCategoryRequest request) {
        assertEnabled();
        FinanceAccess.requireFinanceAccess();
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        Long tenantId = FinanceAccess.requireTenantId();
        String shopId = FinanceAccess.requireShopId();
        ExpenseCategory category = categoryRepository
                .findByIdAndTenantIdAndShopId(id, tenantId, shopId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));
        if (!blank(request.getName())) {
            category.setName(request.getName().trim());
        }
        if (request.getParentId() != null) {
            category.setParentId(request.getParentId());
        }
        if (request.getLedgerAccountId() != null) {
            LedgerAccount account = requireExpenseAccount(tenantId, shopId, request.getLedgerAccountId());
            category.setLedgerAccountId(account.getId());
        }
        if (request.getSortOrder() != null) {
            category.setSortOrder(request.getSortOrder());
        }
        if (request.getActive() != null) {
            category.setActive(request.getActive());
        }
        return categoryRepository.save(category);
    }

    @Transactional
    public List<ExpenseCategory> seedDefaultCategories() {
        assertEnabled();
        FinanceAccess.requireFinanceAccess();
        Long tenantId = FinanceAccess.requireTenantId();
        String shopId = FinanceAccess.requireShopId();
        chartOfAccountsService.seedDefaults();
        JsonNode root = loadTemplate();
        JsonNode categories = root.path("expenseCategories");
        List<ExpenseCategory> result = new ArrayList<>();
        if (categories.isArray()) {
            for (JsonNode row : categories) {
                String code = text(row, "code");
                String name = text(row, "name");
                String ledgerCode = text(row, "ledgerAccountCode");
                String ledgerName = text(row, "ledgerAccountName");
                int sort = row.path("sortOrder").asInt(100);
                if (blank(code) || blank(name) || blank(ledgerCode)) {
                    continue;
                }
                LedgerAccount account = ensureAccount(tenantId, shopId, ledgerCode, ledgerName, "EXPENSE");
                ExpenseCategory existing = categoryRepository
                        .findByTenantIdAndShopIdAndCode(tenantId, shopId, code.toUpperCase(Locale.ROOT))
                        .orElse(null);
                if (existing != null) {
                    result.add(existing);
                    continue;
                }
                ExpenseCategory category = new ExpenseCategory();
                category.setTenantId(tenantId);
                category.setShopId(shopId);
                category.setCode(code.toUpperCase(Locale.ROOT));
                category.setName(name);
                category.setLedgerAccountId(account.getId());
                category.setSortOrder(sort);
                category.setActive(Boolean.TRUE);
                category.setSystemSeed(Boolean.TRUE);
                result.add(categoryRepository.save(category));
            }
        }
        return categoryRepository.findByTenantIdAndShopIdOrderBySortOrderAscCodeAsc(tenantId, shopId);
    }

    @Transactional(readOnly = true)
    public List<IncomeType> listIncomeTypes() {
        assertEnabled();
        FinanceAccess.requireFinanceAccess();
        return incomeTypeRepository.findByTenantIdAndShopIdOrderBySortOrderAscCodeAsc(
                FinanceAccess.requireTenantId(), FinanceAccess.requireShopId());
    }

    @Transactional
    public List<IncomeType> seedDefaultIncomeTypes() {
        assertEnabled();
        FinanceAccess.requireFinanceAccess();
        Long tenantId = FinanceAccess.requireTenantId();
        String shopId = FinanceAccess.requireShopId();
        chartOfAccountsService.seedDefaults();
        JsonNode root = loadTemplate();
        JsonNode types = root.path("incomeTypes");
        if (types.isArray()) {
            for (JsonNode row : types) {
                String code = text(row, "code");
                String name = text(row, "name");
                String ledgerCode = text(row, "ledgerAccountCode");
                String ledgerName = text(row, "ledgerAccountName");
                int sort = row.path("sortOrder").asInt(100);
                if (blank(code) || blank(name) || blank(ledgerCode)) {
                    continue;
                }
                if (incomeTypeRepository.existsByTenantIdAndShopIdAndCode(tenantId, shopId, code.toUpperCase(Locale.ROOT))) {
                    continue;
                }
                LedgerAccount account = ensureAccount(tenantId, shopId, ledgerCode, ledgerName, "INCOME");
                IncomeType type = new IncomeType();
                type.setTenantId(tenantId);
                type.setShopId(shopId);
                type.setCode(code.toUpperCase(Locale.ROOT));
                type.setName(name);
                type.setLedgerAccountId(account.getId());
                type.setSortOrder(sort);
                type.setActive(Boolean.TRUE);
                type.setSystemSeed(Boolean.TRUE);
                incomeTypeRepository.save(type);
            }
        }
        return incomeTypeRepository.findByTenantIdAndShopIdOrderBySortOrderAscCodeAsc(tenantId, shopId);
    }

    private LedgerAccount ensureAccount(
            Long tenantId, String shopId, String code, String name, String accountType) {
        return accountRepository
                .findByTenantIdAndShopIdAndCode(tenantId, shopId, code)
                .orElseGet(() -> {
                    LedgerAccount account = new LedgerAccount();
                    account.setTenantId(tenantId);
                    account.setShopId(shopId);
                    account.setCode(code);
                    account.setName(blank(name) ? code : name);
                    account.setAccountType(accountType);
                    account.setActive(Boolean.TRUE);
                    account.setSystemAccount(Boolean.TRUE);
                    return accountRepository.save(account);
                });
    }

    private LedgerAccount requireExpenseAccount(Long tenantId, String shopId, Long accountId) {
        LedgerAccount account = accountRepository
                .findByIdAndTenantIdAndShopId(accountId, tenantId, shopId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown ledger account: " + accountId));
        if (!"EXPENSE".equalsIgnoreCase(account.getAccountType())) {
            throw new IllegalArgumentException("Category must map to an EXPENSE ledger account");
        }
        return account;
    }

    private JsonNode loadTemplate() {
        try (InputStream in = new ClassPathResource(TEMPLATE_PATH).getInputStream()) {
            return objectMapper.readTree(in);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot load expense seed template: " + TEMPLATE_PATH, ex);
        }
    }

    private void assertEnabled() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Expense module is disabled (ledger.expense-module.enabled=false)");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
