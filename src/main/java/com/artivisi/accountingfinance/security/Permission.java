package com.artivisi.accountingfinance.security;

import com.artivisi.accountingfinance.enums.Role;
import java.util.Set;

/**
 * Permission constants and role-permission mapping.
 * Permissions are additive - users get combined permissions from all their roles.
 */
public final class Permission {

    private Permission() {}

    // Dashboard
    public static final String DASHBOARD_VIEW = "DASHBOARD_VIEW";

    // Transactions
    public static final String TRANSACTION_VIEW = "TRANSACTION_VIEW";
    public static final String TRANSACTION_CREATE = "TRANSACTION_CREATE";
    public static final String TRANSACTION_EDIT = "TRANSACTION_EDIT";
    public static final String TRANSACTION_POST = "TRANSACTION_POST";
    public static final String TRANSACTION_VOID = "TRANSACTION_VOID";
    public static final String TRANSACTION_DELETE = "TRANSACTION_DELETE";

    // Drafts (Telegram receipts)
    public static final String DRAFT_VIEW = "DRAFT_VIEW";
    public static final String DRAFT_APPROVE = "DRAFT_APPROVE";
    public static final String DRAFT_REJECT = "DRAFT_REJECT";

    // Journals
    public static final String JOURNAL_VIEW = "JOURNAL_VIEW";
    public static final String JOURNAL_CREATE = "JOURNAL_CREATE";
    public static final String JOURNAL_EDIT = "JOURNAL_EDIT";
    public static final String JOURNAL_POST = "JOURNAL_POST";
    public static final String JOURNAL_VOID = "JOURNAL_VOID";

    // Reports
    public static final String REPORT_VIEW = "REPORT_VIEW";
    public static final String REPORT_EXPORT = "REPORT_EXPORT";
    public static final String TAX_REPORT_VIEW = "TAX_REPORT_VIEW";
    public static final String TAX_EXPORT = "TAX_EXPORT";

    // Chart of Accounts
    public static final String ACCOUNT_VIEW = "ACCOUNT_VIEW";
    public static final String ACCOUNT_CREATE = "ACCOUNT_CREATE";
    public static final String ACCOUNT_EDIT = "ACCOUNT_EDIT";
    public static final String ACCOUNT_DELETE = "ACCOUNT_DELETE";

    // Templates
    public static final String TEMPLATE_VIEW = "TEMPLATE_VIEW";
    public static final String TEMPLATE_CREATE = "TEMPLATE_CREATE";
    public static final String TEMPLATE_EDIT = "TEMPLATE_EDIT";
    public static final String TEMPLATE_DELETE = "TEMPLATE_DELETE";

    // Clients
    public static final String CLIENT_VIEW = "CLIENT_VIEW";
    public static final String CLIENT_CREATE = "CLIENT_CREATE";
    public static final String CLIENT_EDIT = "CLIENT_EDIT";
    public static final String CLIENT_DELETE = "CLIENT_DELETE";

    // Projects
    public static final String PROJECT_VIEW = "PROJECT_VIEW";
    public static final String PROJECT_CREATE = "PROJECT_CREATE";
    public static final String PROJECT_EDIT = "PROJECT_EDIT";
    public static final String PROJECT_DELETE = "PROJECT_DELETE";

    // Invoices
    public static final String INVOICE_VIEW = "INVOICE_VIEW";
    public static final String INVOICE_CREATE = "INVOICE_CREATE";
    public static final String INVOICE_EDIT = "INVOICE_EDIT";
    public static final String INVOICE_SEND = "INVOICE_SEND";
    public static final String INVOICE_MARK_PAID = "INVOICE_MARK_PAID";

    // Amortization
    public static final String AMORTIZATION_VIEW = "AMORTIZATION_VIEW";
    public static final String AMORTIZATION_CREATE = "AMORTIZATION_CREATE";
    public static final String AMORTIZATION_EDIT = "AMORTIZATION_EDIT";
    public static final String AMORTIZATION_POST = "AMORTIZATION_POST";

    // Tax Calendar
    public static final String TAX_CALENDAR_VIEW = "TAX_CALENDAR_VIEW";
    public static final String TAX_CALENDAR_MANAGE = "TAX_CALENDAR_MANAGE";

    // Fiscal Periods
    public static final String FISCAL_PERIOD_VIEW = "FISCAL_PERIOD_VIEW";
    public static final String FISCAL_PERIOD_MANAGE = "FISCAL_PERIOD_MANAGE";

    // Employees
    public static final String EMPLOYEE_VIEW = "EMPLOYEE_VIEW";
    public static final String EMPLOYEE_CREATE = "EMPLOYEE_CREATE";
    public static final String EMPLOYEE_EDIT = "EMPLOYEE_EDIT";
    public static final String EMPLOYEE_DELETE = "EMPLOYEE_DELETE";

    // Salary Components
    public static final String SALARY_COMPONENT_VIEW = "SALARY_COMPONENT_VIEW";
    public static final String SALARY_COMPONENT_MANAGE = "SALARY_COMPONENT_MANAGE";

    // Payroll
    public static final String PAYROLL_VIEW = "PAYROLL_VIEW";
    public static final String PAYROLL_CREATE = "PAYROLL_CREATE";
    public static final String PAYROLL_APPROVE = "PAYROLL_APPROVE";
    public static final String PAYROLL_POST = "PAYROLL_POST";
    public static final String PAYROLL_CANCEL = "PAYROLL_CANCEL";
    public static final String PAYROLL_EXPORT = "PAYROLL_EXPORT";

    // Calculators (BPJS, PPh 21)
    public static final String CALCULATOR_USE = "CALCULATOR_USE";

    // Data Import
    public static final String DATA_IMPORT = "DATA_IMPORT";

    // Settings
    public static final String SETTINGS_VIEW = "SETTINGS_VIEW";
    public static final String SETTINGS_EDIT = "SETTINGS_EDIT";

    // User Management (Admin only)
    public static final String USER_VIEW = "USER_VIEW";
    public static final String USER_CREATE = "USER_CREATE";
    public static final String USER_EDIT = "USER_EDIT";
    public static final String USER_DELETE = "USER_DELETE";
    public static final String USER_ASSIGN_ROLES = "USER_ASSIGN_ROLES";

    // Employee Self-Service
    public static final String OWN_PAYSLIP_VIEW = "OWN_PAYSLIP_VIEW";
    public static final String OWN_PROFILE_VIEW = "OWN_PROFILE_VIEW";
    public static final String OWN_PROFILE_EDIT = "OWN_PROFILE_EDIT";

    /**
     * Get all permissions for a given role.
     */
    public static Set<String> getPermissionsForRole(Role role) {
        return switch (role) {
            case ADMIN -> Set.of(
                // All permissions
                DASHBOARD_VIEW,
                TRANSACTION_VIEW, TRANSACTION_CREATE, TRANSACTION_EDIT, TRANSACTION_POST, TRANSACTION_VOID, TRANSACTION_DELETE,
                DRAFT_VIEW, DRAFT_APPROVE, DRAFT_REJECT,
                JOURNAL_VIEW, JOURNAL_CREATE, JOURNAL_EDIT, JOURNAL_POST, JOURNAL_VOID,
                REPORT_VIEW, REPORT_EXPORT, TAX_REPORT_VIEW, TAX_EXPORT,
                ACCOUNT_VIEW, ACCOUNT_CREATE, ACCOUNT_EDIT, ACCOUNT_DELETE,
                TEMPLATE_VIEW, TEMPLATE_CREATE, TEMPLATE_EDIT, TEMPLATE_DELETE,
                CLIENT_VIEW, CLIENT_CREATE, CLIENT_EDIT, CLIENT_DELETE,
                PROJECT_VIEW, PROJECT_CREATE, PROJECT_EDIT, PROJECT_DELETE,
                INVOICE_VIEW, INVOICE_CREATE, INVOICE_EDIT, INVOICE_SEND, INVOICE_MARK_PAID,
                AMORTIZATION_VIEW, AMORTIZATION_CREATE, AMORTIZATION_EDIT, AMORTIZATION_POST,
                TAX_CALENDAR_VIEW, TAX_CALENDAR_MANAGE,
                FISCAL_PERIOD_VIEW, FISCAL_PERIOD_MANAGE,
                EMPLOYEE_VIEW, EMPLOYEE_CREATE, EMPLOYEE_EDIT, EMPLOYEE_DELETE,
                SALARY_COMPONENT_VIEW, SALARY_COMPONENT_MANAGE,
                PAYROLL_VIEW, PAYROLL_CREATE, PAYROLL_APPROVE, PAYROLL_POST, PAYROLL_CANCEL, PAYROLL_EXPORT,
                CALCULATOR_USE,
                DATA_IMPORT,
                SETTINGS_VIEW, SETTINGS_EDIT,
                USER_VIEW, USER_CREATE, USER_EDIT, USER_DELETE, USER_ASSIGN_ROLES,
                OWN_PAYSLIP_VIEW, OWN_PROFILE_VIEW, OWN_PROFILE_EDIT
            );

            case OWNER -> Set.of(
                // All business features, no user management
                DASHBOARD_VIEW,
                TRANSACTION_VIEW, TRANSACTION_CREATE, TRANSACTION_EDIT, TRANSACTION_POST, TRANSACTION_VOID, TRANSACTION_DELETE,
                DRAFT_VIEW, DRAFT_APPROVE, DRAFT_REJECT,
                JOURNAL_VIEW, JOURNAL_CREATE, JOURNAL_EDIT, JOURNAL_POST, JOURNAL_VOID,
                REPORT_VIEW, REPORT_EXPORT, TAX_REPORT_VIEW, TAX_EXPORT,
                ACCOUNT_VIEW, ACCOUNT_CREATE, ACCOUNT_EDIT, ACCOUNT_DELETE,
                TEMPLATE_VIEW, TEMPLATE_CREATE, TEMPLATE_EDIT, TEMPLATE_DELETE,
                CLIENT_VIEW, CLIENT_CREATE, CLIENT_EDIT, CLIENT_DELETE,
                PROJECT_VIEW, PROJECT_CREATE, PROJECT_EDIT, PROJECT_DELETE,
                INVOICE_VIEW, INVOICE_CREATE, INVOICE_EDIT, INVOICE_SEND, INVOICE_MARK_PAID,
                AMORTIZATION_VIEW, AMORTIZATION_CREATE, AMORTIZATION_EDIT, AMORTIZATION_POST,
                TAX_CALENDAR_VIEW, TAX_CALENDAR_MANAGE,
                FISCAL_PERIOD_VIEW, FISCAL_PERIOD_MANAGE,
                EMPLOYEE_VIEW, EMPLOYEE_CREATE, EMPLOYEE_EDIT, EMPLOYEE_DELETE,
                SALARY_COMPONENT_VIEW, SALARY_COMPONENT_MANAGE,
                PAYROLL_VIEW, PAYROLL_CREATE, PAYROLL_APPROVE, PAYROLL_POST, PAYROLL_CANCEL, PAYROLL_EXPORT,
                CALCULATOR_USE,
                DATA_IMPORT,
                SETTINGS_VIEW, SETTINGS_EDIT,
                OWN_PAYSLIP_VIEW, OWN_PROFILE_VIEW, OWN_PROFILE_EDIT
            );

            case ACCOUNTANT -> Set.of(
                // Accounting operations and reports
                DASHBOARD_VIEW,
                TRANSACTION_VIEW, TRANSACTION_CREATE, TRANSACTION_EDIT, TRANSACTION_POST, TRANSACTION_VOID,
                DRAFT_VIEW, DRAFT_APPROVE, DRAFT_REJECT,
                JOURNAL_VIEW, JOURNAL_CREATE, JOURNAL_EDIT, JOURNAL_POST, JOURNAL_VOID,
                REPORT_VIEW, REPORT_EXPORT, TAX_REPORT_VIEW, TAX_EXPORT,
                ACCOUNT_VIEW,
                TEMPLATE_VIEW, TEMPLATE_CREATE, TEMPLATE_EDIT,
                CLIENT_VIEW, CLIENT_CREATE, CLIENT_EDIT,
                PROJECT_VIEW, PROJECT_CREATE, PROJECT_EDIT,
                INVOICE_VIEW, INVOICE_CREATE, INVOICE_EDIT, INVOICE_SEND, INVOICE_MARK_PAID,
                AMORTIZATION_VIEW, AMORTIZATION_CREATE, AMORTIZATION_EDIT, AMORTIZATION_POST,
                TAX_CALENDAR_VIEW, TAX_CALENDAR_MANAGE,
                FISCAL_PERIOD_VIEW, FISCAL_PERIOD_MANAGE,
                EMPLOYEE_VIEW,
                SALARY_COMPONENT_VIEW,
                PAYROLL_VIEW, PAYROLL_CREATE, PAYROLL_APPROVE, PAYROLL_POST, PAYROLL_EXPORT,
                CALCULATOR_USE,
                OWN_PAYSLIP_VIEW, OWN_PROFILE_VIEW, OWN_PROFILE_EDIT
            );

            case STAFF -> Set.of(
                // Limited daily operations
                DASHBOARD_VIEW,
                TRANSACTION_VIEW, TRANSACTION_CREATE,
                DRAFT_VIEW,
                JOURNAL_VIEW,
                REPORT_VIEW,
                ACCOUNT_VIEW,
                TEMPLATE_VIEW,
                CLIENT_VIEW,
                PROJECT_VIEW,
                INVOICE_VIEW,
                CALCULATOR_USE,
                OWN_PAYSLIP_VIEW, OWN_PROFILE_VIEW, OWN_PROFILE_EDIT
            );

            case AUDITOR -> Set.of(
                // Read-only access to all reports
                DASHBOARD_VIEW,
                TRANSACTION_VIEW,
                DRAFT_VIEW,
                JOURNAL_VIEW,
                REPORT_VIEW, REPORT_EXPORT, TAX_REPORT_VIEW, TAX_EXPORT,
                ACCOUNT_VIEW,
                TEMPLATE_VIEW,
                CLIENT_VIEW,
                PROJECT_VIEW,
                INVOICE_VIEW,
                AMORTIZATION_VIEW,
                TAX_CALENDAR_VIEW,
                FISCAL_PERIOD_VIEW,
                EMPLOYEE_VIEW,
                SALARY_COMPONENT_VIEW,
                PAYROLL_VIEW, PAYROLL_EXPORT,
                CALCULATOR_USE,
                OWN_PROFILE_VIEW
            );

            case EMPLOYEE -> Set.of(
                // Own payslips and profile only
                OWN_PAYSLIP_VIEW,
                OWN_PROFILE_VIEW,
                OWN_PROFILE_EDIT
            );
        };
    }

    /**
     * Get combined permissions for multiple roles.
     */
    public static Set<String> getPermissionsForRoles(Set<Role> roles) {
        Set<String> permissions = new java.util.HashSet<>();
        for (Role role : roles) {
            permissions.addAll(getPermissionsForRole(role));
        }
        return permissions;
    }
}
