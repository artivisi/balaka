package com.artivisi.accountingfinance.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry of OAuth scopes accepted by the REST API.
 *
 * <p>A bearer token only receives the {@code SCOPE_x} authority when the token
 * carries scope {@code x} AND the token's user currently holds at least one of
 * the scope's {@link #grantingPermissions()}. Endpoints whose operations need a
 * narrower permission (e.g. void vs. create) additionally check that permission.
 */
public enum ApiScope {

    DRAFTS_READ("drafts:read", "Read draft transaction details",
            Permission.DRAFT_VIEW),
    DRAFTS_CREATE("drafts:create", "Create and edit draft transactions from receipt/text",
            Permission.TRANSACTION_CREATE),
    DRAFTS_APPROVE("drafts:approve", "Approve or reject draft transactions",
            Permission.DRAFT_APPROVE, Permission.DRAFT_REJECT),
    ANALYSIS_READ("analysis:read", "Read financial statements, ledgers, transactions and analysis reports",
            Permission.REPORT_VIEW),
    ANALYSIS_WRITE("analysis:write", "Publish AI-generated analysis reports",
            Permission.ANALYSIS_REPORT_CREATE),
    TRANSACTIONS_POST("transactions:post",
            "Create, update, post, void, delete and purge transactions; transaction documents and tax details",
            Permission.TRANSACTION_CREATE, Permission.TRANSACTION_EDIT, Permission.TRANSACTION_POST,
            Permission.TRANSACTION_VOID, Permission.TRANSACTION_DELETE, Permission.TRANSACTION_PURGE),
    TEMPLATES_READ("templates:read", "Read journal templates",
            Permission.TEMPLATE_VIEW),
    TEMPLATES_WRITE("templates:write", "Create, update and delete journal templates",
            Permission.TEMPLATE_CREATE, Permission.TEMPLATE_EDIT, Permission.TEMPLATE_DELETE),
    DATA_IMPORT("data:import", "Import bulk data via ZIP",
            Permission.DATA_IMPORT),
    BILLS_READ("bills:read", "Read vendor bills",
            Permission.BILL_VIEW),
    BILLS_CREATE("bills:create", "Create vendor bills",
            Permission.BILL_CREATE),
    BILLS_APPROVE("bills:approve", "Approve vendor bills and mark them paid",
            Permission.BILL_APPROVE, Permission.BILL_MARK_PAID),
    TAX_EXPORT_READ("tax-export:read",
            "Export tax data (e-Faktur, e-Bupot, PPN/PPh, SPT); read fiscal adjustments, employees, salary components and payroll",
            Permission.TAX_EXPORT),
    TAX_EXPORT_WRITE("tax-export:write", "Create, update and delete fiscal adjustments and loss carryforwards",
            Permission.FISCAL_PERIOD_MANAGE),
    PAYROLL_WRITE("payroll:write",
            "Create, calculate, approve, post and delete payroll runs; payroll schedule; employees and salary components",
            Permission.PAYROLL_CREATE, Permission.PAYROLL_APPROVE, Permission.PAYROLL_POST,
            Permission.EMPLOYEE_CREATE, Permission.EMPLOYEE_EDIT, Permission.SALARY_COMPONENT_MANAGE),
    ACCOUNTS_READ("accounts:read", "Read chart of accounts",
            Permission.ACCOUNT_VIEW),
    ACCOUNTS_WRITE("accounts:write", "Create, update and delete accounts",
            Permission.ACCOUNT_CREATE, Permission.ACCOUNT_EDIT, Permission.ACCOUNT_DELETE),
    ASSETS_READ("assets:read", "Read fixed assets, asset categories and depreciation entries",
            Permission.ASSET_VIEW),
    ASSETS_WRITE("assets:write", "Register, update and delete fixed assets; generate, post and skip depreciation",
            Permission.ASSET_CREATE, Permission.ASSET_EDIT, Permission.ASSET_DELETE, Permission.ASSET_DEPRECIATE),
    TAX_FILINGS_READ("tax-filings:read", "Read the tax filing register and its attached documents",
            Permission.TAX_CALENDAR_VIEW),
    TAX_FILINGS_WRITE("tax-filings:write", "Register tax filings and attach SPT/BPE/STP documents",
            Permission.TAX_CALENDAR_MANAGE),
    BANK_RECON_READ("bank-recon:read", "Read bank parser configs, statements and reconciliations",
            Permission.BANK_RECONCILIATION_VIEW),
    BANK_RECON_WRITE("bank-recon:write",
            "Configure parsers, import statements, match items, create transactions from and complete reconciliations",
            Permission.BANK_RECONCILIATION_IMPORT, Permission.BANK_RECONCILIATION_MATCH,
            Permission.BANK_RECONCILIATION_COMPLETE, Permission.BANK_RECONCILIATION_CONFIG);

    private static final Map<String, ApiScope> BY_NAME = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(ApiScope::scope, Function.identity()));

    private final String scope;
    private final String description;
    private final Set<String> grantingPermissions;

    ApiScope(String scope, String description, String... grantingPermissions) {
        this.scope = scope;
        this.description = description;
        this.grantingPermissions = Set.of(grantingPermissions);
    }

    public String scope() {
        return scope;
    }

    public String description() {
        return description;
    }

    public Set<String> grantingPermissions() {
        return grantingPermissions;
    }

    public boolean isGrantedTo(Collection<String> permissions) {
        return grantingPermissions.stream().anyMatch(permissions::contains);
    }

    public static Optional<ApiScope> fromScope(String scope) {
        return Optional.ofNullable(BY_NAME.get(scope));
    }

    public static List<String> allScopes() {
        return Arrays.stream(values()).map(ApiScope::scope).toList();
    }

    /**
     * Comma-separated list of every registered scope, as stored on device-flow tokens.
     */
    public static String allScopesJoined() {
        return String.join(",", allScopes());
    }
}
