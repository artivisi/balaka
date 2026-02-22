package com.artivisi.accountingfinance.controller;

/**
 * Constants for view-related strings used across controllers.
 * Helps avoid string literal duplication flagged by SonarCloud.
 */
public final class ViewConstants {

    private ViewConstants() {
        // Utility class - prevent instantiation
    }

    // Model attribute names
    public static final String ATTR_CURRENT_PAGE = "currentPage";
    public static final String ATTR_START_DATE = "startDate";
    public static final String ATTR_END_DATE = "endDate";
    public static final String ATTR_COMPANY = "company";
    public static final String ATTR_REPORT = "report";
    public static final String ATTR_PRODUCTS = "products";
    public static final String ATTR_CATEGORIES = "categories";
    public static final String ATTR_ACCOUNTS = "accounts";
    public static final String ATTR_TEMPLATES = "templates";
    public static final String ATTR_IS_EDIT = "isEdit";

    // Current page values (navigation)
    public static final String PAGE_DASHBOARD = "dashboard";
    public static final String PAGE_ACCOUNTS = "accounts";
    public static final String PAGE_TRANSACTIONS = "transactions";
    public static final String PAGE_TEMPLATES = "templates";
    public static final String PAGE_REPORTS = "reports";
    public static final String PAGE_INVOICES = "invoices";
    public static final String PAGE_EMPLOYEES = "employees";
    public static final String PAGE_PAYROLL = "payroll";
    public static final String PAGE_SETTINGS = "settings";
    public static final String PAGE_PRODUCTS = "products";
    public static final String PAGE_INVENTORY = "inventory";
    public static final String PAGE_CLIENTS = "clients";
    public static final String PAGE_PROJECTS = "projects";
    public static final String PAGE_FIXED_ASSETS = "fixed-assets";
    public static final String PAGE_AMORTIZATION = "amortization";
    public static final String PAGE_ABOUT = "about";
    public static final String PAGE_ASSETS = "assets";
    public static final String PAGE_BPJS_CALCULATOR = "bpjs-calculator";
    public static final String PAGE_DRAFTS = "drafts";
    public static final String PAGE_JOURNALS = "journals";
    public static final String PAGE_PRODUCT_CATEGORIES = "product-categories";
    public static final String PAGE_SALARY_COMPONENTS = "salary-components";
    public static final String PAGE_INVENTORY_PURCHASE = "inventory-purchase";
    public static final String PAGE_INVENTORY_SALE = "inventory-sale";
    public static final String PAGE_INVENTORY_ADJUSTMENT = "inventory-adjustment";
    public static final String PAGE_INVENTORY_STOCK = "inventory-stock";
    public static final String PAGE_INVENTORY_TRANSACTIONS = "inventory-transactions";

    // Current page values - Tags
    public static final String PAGE_TAG_TYPES = "tag-types";
    public static final String PAGE_TAGS = "tags";

    // Current page values - Device Management
    public static final String PAGE_DEVICES = "devices";

    // Current page values - Analysis Reports
    public static final String PAGE_ANALYSIS_REPORTS = "analysis-reports";

    // Current page values - Smart Alerts
    public static final String PAGE_ALERTS = "alerts";
    public static final String PAGE_ALERT_CONFIG = "alert-config";
    public static final String PAGE_ALERT_HISTORY = "alert-history";

    // Current page values - Vendors
    public static final String PAGE_VENDORS = "vendors";

    // Current page values - Aging Reports
    public static final String PAGE_AGING_RECEIVABLES = "aging-receivables";
    public static final String PAGE_AGING_PAYABLES = "aging-payables";

    // Current page values - Statements
    public static final String PAGE_CLIENT_STATEMENT = "client-statement";
    public static final String PAGE_VENDOR_STATEMENT = "vendor-statement";

    // Current page values - Bills
    public static final String PAGE_BILLS = "bills";

    // Current page values - Recurring Transactions
    public static final String PAGE_RECURRING = "recurring";

    // Current page values - Bank Reconciliation
    public static final String PAGE_BANK_RECONCILIATION = "bank-reconciliation";
    public static final String PAGE_BANK_RECON_PARSER_CONFIGS = "bank-recon-parser-configs";
    public static final String PAGE_BANK_RECON_STATEMENTS = "bank-recon-statements";
    public static final String PAGE_BANK_RECON_RECONCILIATIONS = "bank-recon-reconciliations";

    // View paths - Products
    public static final String VIEW_PRODUCTS_LIST = "products/list";
    public static final String VIEW_PRODUCTS_FORM = "products/form";
    public static final String VIEW_PRODUCTS_DETAIL = "products/detail";
    public static final String VIEW_CATEGORIES_LIST = "products/categories/list";
    public static final String VIEW_CATEGORIES_FORM = "products/categories/form";

    // View paths - Invoices
    public static final String VIEW_INVOICES_LIST = "invoices/list";
    public static final String VIEW_INVOICES_FORM = "invoices/form";
    public static final String VIEW_INVOICES_DETAIL = "invoices/detail";

    // View paths - Payroll
    public static final String VIEW_PAYROLL_LIST = "payroll/list";
    public static final String VIEW_PAYROLL_FORM = "payroll/form";
    public static final String VIEW_PAYROLL_DETAIL = "payroll/detail";
    public static final String VIEW_SALARY_COMPONENTS_FORM = "salary-components/form";

    // View paths - Vendors
    public static final String VIEW_VENDORS_LIST = "vendors/list";
    public static final String VIEW_VENDORS_FORM = "vendors/form";
    public static final String VIEW_VENDORS_DETAIL = "vendors/detail";

    // View paths - Bills
    public static final String VIEW_BILLS_LIST = "bills/list";
    public static final String VIEW_BILLS_FORM = "bills/form";
    public static final String VIEW_BILLS_DETAIL = "bills/detail";

    // Redirect prefixes - Vendors and Bills
    public static final String REDIRECT_VENDORS = "redirect:/vendors/";
    public static final String REDIRECT_BILLS = "redirect:/bills/";

    // Redirect prefixes - Bank Reconciliation
    public static final String REDIRECT_BANK_RECON = "redirect:/bank-reconciliation/";
    public static final String REDIRECT_BANK_RECON_PARSER_CONFIGS = "redirect:/bank-reconciliation/parser-configs";
    public static final String REDIRECT_BANK_RECON_STATEMENTS = "redirect:/bank-reconciliation/statements";
    public static final String REDIRECT_BANK_RECON_RECONCILIATIONS = "redirect:/bank-reconciliation/reconciliations";

    // Redirect prefixes - Recurring Transactions
    public static final String REDIRECT_RECURRING = "redirect:/recurring";

    // Redirect prefixes
    public static final String REDIRECT_INVOICES = "redirect:/invoices/";
    public static final String REDIRECT_PAYROLL = "redirect:/payroll/";
    public static final String REDIRECT_PRODUCTS = "redirect:/products/";
    public static final String REDIRECT_TRANSACTIONS = "redirect:/transactions/";
    public static final String REDIRECT_TEMPLATES = "redirect:/templates/";
    public static final String REDIRECT_EMPLOYEES = "redirect:/employees/";

    // Inventory transaction types
    public static final String INVENTORY_PURCHASE = "inventory-purchase";
    public static final String INVENTORY_SALE = "inventory-sale";
    public static final String INVENTORY_ADJUSTMENT = "inventory-adjustment";
    public static final String INVENTORY_PRODUCTION_IN = "inventory-production-in";
    public static final String INVENTORY_PRODUCTION_OUT = "inventory-production-out";

    // Error messages
    public static final String ERROR_PRODUCT_NOT_FOUND = "Produk tidak ditemukan: ";
    public static final String ERROR_TEMPLATE_NOT_FOUND = "Template tidak ditemukan: ";
}
