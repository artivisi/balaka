package com.artivisi.accountingfinance.security;

import org.junit.jupiter.api.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;

/**
 * Comprehensive OWASP ZAP DAST Security Tests
 *
 * Tests OWASP Top 10 vulnerabilities using graybox approach:
 * - SQL Injection (A03:2021)
 * - XSS (A03:2021)
 * - Broken Authentication (A07:2021)
 * - Security Misconfiguration (A05:2021)
 * - IDOR (A01:2021)
 *
 * Endpoints enumerated from controller mappings (graybox, not blackbox spider).
 *
 * Run locally:
 *   ./mvnw test -Dtest=ZapDastTest -DexcludedGroups="" -Ddast.enabled=true
 *
 * Quick scan (passive only):
 *   ./mvnw test -Dtest=ZapDastTest -DexcludedGroups="" -Ddast.enabled=true -Ddast.quick=true
 */
@DisplayName("OWASP ZAP DAST Security Scan")
class ZapDastTest extends ZapDastTestBase {

    // OWASP Top 10 test payloads
    private static final String[] SQL_INJECTION_PAYLOADS = {
            "' OR '1'='1",
            "1; DROP TABLE users--",
            "' UNION SELECT * FROM users--",
            "admin'--",
            "1' AND '1'='1",
            "'; WAITFOR DELAY '0:0:5'--"
    };

    private static final String[] XSS_PAYLOADS = {
            "<script>alert('XSS')</script>",
            "<img src=x onerror=alert('XSS')>",
            "javascript:alert('XSS')",
            "<svg onload=alert('XSS')>",
            "{{7*7}}",
            "${7*7}",
            "<iframe src='javascript:alert(1)'>"
    };

    private static final String[] PATH_TRAVERSAL_PAYLOADS = {
            "../../../etc/passwd",
            "....//....//etc/passwd",
            "..%2f..%2f..%2fetc/passwd",
            "..\\..\\..\\etc\\passwd"
    };

    // ========== ENDPOINT ENUMERATION FROM CONTROLLER MAPPINGS ==========

    // List pages with search/filter functionality (GET with query params)
    private static final String[] LIST_PAGES = {
            "/accounts",
            "/transactions",
            "/templates",
            "/employees",
            "/products",
            "/products/categories",
            "/clients",
            "/projects",
            "/invoices",
            "/payroll",
            "/assets",
            "/assets/categories",
            "/inventory/stock",
            "/inventory/transactions",
            "/inventory/bom",
            "/inventory/production",
            "/amortization",
            "/drafts",
            "/users",
            "/fiscal-periods",
            "/salary-components",
            "/tax-calendar"
    };

    // Form pages (GET /new or /create for form, POST for submission)
    private static final String[][] FORM_ENDPOINTS = {
            // {GET form path, POST path, field1, field2, field3...}
            {"/accounts/new", "/accounts/new", "accountCode", "accountName", "accountType"},
            {"/employees/new", "/employees/new", "employeeNumber", "name", "email", "phone"},
            {"/products/new", "/products/new", "code", "name", "description"},
            {"/clients/new", "/clients/new", "clientCode", "name", "email"},
            {"/projects/new", "/projects/new", "projectCode", "projectName", "clientCode"},
            {"/templates/new", "/templates", "templateCode", "name", "description"},
            {"/assets/new", "/assets/new", "assetCode", "assetName", "purchasePrice"},
            {"/assets/categories/new", "/assets/categories/new", "categoryCode", "name"},
            {"/products/categories/new", "/products/categories/new", "code", "name"},
            {"/salary-components/new", "/salary-components/new", "code", "name", "componentType"},
            {"/users/new", "/users", "username", "email", "password"},
            {"/invoices/new", "/invoices/new", "clientCode", "dueDate", "amount"},
            {"/inventory/bom/create", "/inventory/bom/save", "productId", "quantity"},
            {"/inventory/production/create", "/inventory/production/save", "bomId", "quantity"},
            {"/amortization/new", "/amortization", "description", "totalAmount", "termMonths"}
    };

    // Parameterized endpoints with {id} (for IDOR testing)
    private static final String[] PARAMETERIZED_ENDPOINTS = {
            "/accounts/{id}",
            "/accounts/{id}/edit",
            "/employees/{id}",
            "/employees/{id}/edit",
            "/products/{id}",
            "/products/{id}/edit",
            "/clients/{id}",
            "/clients/{id}/edit",
            "/projects/{id}",
            "/projects/{id}/edit",
            "/templates/{id}",
            "/templates/{id}/edit",
            "/templates/{id}/execute",
            "/transactions/{id}",
            "/transactions/{id}/edit",
            "/invoices/{id}",
            "/invoices/{id}/edit",
            "/assets/{id}",
            "/assets/{id}/edit",
            "/assets/{id}/dispose",
            "/users/{id}",
            "/users/{id}/edit",
            "/amortization/{id}",
            "/amortization/{id}/edit",
            "/drafts/{id}",
            "/payroll/{id}",
            "/inventory/bom/{id}",
            "/inventory/bom/{id}/edit",
            "/inventory/production/{id}",
            "/inventory/production/{id}/edit",
            "/inventory/transactions/{id}",
            "/inventory/stock/{id}",
            "/salary-components/{id}",
            "/salary-components/{id}/edit",
            "/journals/ledger/{id}",
            "/settings/data-subjects/{id}",
            "/settings/data-subjects/{id}/export",
            "/settings/data-subjects/{id}/anonymize"
    };

    // API endpoints (REST)
    private static final String[] API_ENDPOINTS = {
            "/templates/api",
            "/templates/api/recent",
            "/templates/api/search",
            "/transactions/api",
            "/transactions/api/search",
            "/journals/api",
            "/drafts/api",
            "/reports/api/trial-balance",
            "/reports/api/income-statement",
            "/reports/api/balance-sheet",
            "/reports/api/ppn-summary",
            "/reports/api/pph23-withholding",
            "/reports/api/tax-summary",
            "/reports/api/depreciation",
            "/reports/api/project-profitability",
            "/reports/api/client-profitability",
            "/reports/api/cost-overrun",
            "/reports/api/fiscal-closing/preview",
            "/tax-calendar/api/widget",
            "/documents/api/{id}",
            "/documents/api/transaction/{id}"
    };

    // Report pages with date filters
    private static final String[] REPORT_PAGES = {
            "/reports/trial-balance",
            "/reports/income-statement",
            "/reports/balance-sheet",
            "/reports/cash-flow",
            "/reports/ppn-summary",
            "/reports/pph23-withholding",
            "/reports/tax-summary",
            "/reports/depreciation",
            "/reports/project-profitability",
            "/reports/client-profitability",
            "/reports/client-ranking",
            "/reports/fiscal-closing",
            "/inventory/reports/stock-balance",
            "/inventory/reports/stock-movement",
            "/inventory/reports/valuation",
            "/inventory/reports/profitability"
    };

    // Test UUIDs for parameterized endpoints
    private static final String[] TEST_IDS = {
            "00000000-0000-0000-0000-000000000001",
            "11111111-1111-1111-1111-111111111111",
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    };

    @Test
    @DisplayName("Should pass SQL injection security scan on all endpoints")
    void shouldPassSqlInjectionScan() throws Exception {
        log.info("=== Starting SQL Injection Security Scan ===");
        log.info("Testing {} list pages, {} form endpoints, {} parameterized endpoints",
                LIST_PAGES.length, FORM_ENDPOINTS.length, PARAMETERIZED_ENDPOINTS.length);

        authenticatedClient = performLogin("admin", "admin");

        // 1. Test SQLi on all list pages with search params
        log.info("Testing SQL injection on {} list pages...", LIST_PAGES.length);
        for (String page : LIST_PAGES) {
            accessPage(authenticatedClient, page);
            for (String payload : SQL_INJECTION_PAYLOADS) {
                accessPageWithParams(page, "search=" + urlEncode(payload));
                accessPageWithParams(page, "filter=" + urlEncode(payload));
            }
        }

        // 2. Test SQLi in form submissions
        log.info("Testing SQL injection in {} form endpoints...", FORM_ENDPOINTS.length);
        for (String[] formEndpoint : FORM_ENDPOINTS) {
            String formPath = formEndpoint[0];
            String postPath = formEndpoint[1];
            submitFormWithPayloads(formPath, postPath, formEndpoint, SQL_INJECTION_PAYLOADS);
        }

        // 3. Test SQLi in parameterized URLs
        log.info("Testing SQL injection in {} parameterized endpoints...", PARAMETERIZED_ENDPOINTS.length);
        for (String endpoint : PARAMETERIZED_ENDPOINTS) {
            for (String payload : SQL_INJECTION_PAYLOADS) {
                String path = endpoint.replace("{id}", urlEncode(payload));
                accessPage(authenticatedClient, path);
            }
        }

        // 4. Test SQLi in login form (unauthenticated)
        log.info("Testing SQL injection in login form...");
        for (String payload : SQL_INJECTION_PAYLOADS) {
            testLoginWithPayload(payload);
        }

        // 5. Test SQLi in report filters
        log.info("Testing SQL injection in report filters...");
        for (String report : REPORT_PAGES) {
            for (String payload : SQL_INJECTION_PAYLOADS) {
                accessPageWithParams(report, "search=" + urlEncode(payload));
            }
        }

        waitForPassiveScan();

        if (!QUICK_SCAN) {
            runActiveScan(targetUrl);
        }

        ScanResults results = analyzeAlerts("SQLInjection");
        generateHtmlReport("zap-sqli-report.html");

        assertSecurityThresholds(results, "SQLInjection");
    }

    @Test
    @DisplayName("Should pass XSS security scan on all endpoints")
    void shouldPassXssScan() throws Exception {
        log.info("=== Starting XSS Security Scan ===");
        log.info("Testing {} list pages, {} form endpoints, {} parameterized endpoints",
                LIST_PAGES.length, FORM_ENDPOINTS.length, PARAMETERIZED_ENDPOINTS.length);

        authenticatedClient = performLogin("admin", "admin");

        // 1. Test XSS on all list pages with search params
        log.info("Testing XSS on {} list pages...", LIST_PAGES.length);
        for (String page : LIST_PAGES) {
            accessPage(authenticatedClient, page);
            for (String payload : XSS_PAYLOADS) {
                accessPageWithParams(page, "search=" + urlEncode(payload));
                accessPageWithParams(page, "filter=" + urlEncode(payload));
            }
        }

        // 2. Test XSS in form submissions
        log.info("Testing XSS in {} form endpoints...", FORM_ENDPOINTS.length);
        for (String[] formEndpoint : FORM_ENDPOINTS) {
            String formPath = formEndpoint[0];
            String postPath = formEndpoint[1];
            submitFormWithPayloads(formPath, postPath, formEndpoint, XSS_PAYLOADS);
        }

        // 3. Test XSS in parameterized URLs
        log.info("Testing XSS in {} parameterized endpoints...", PARAMETERIZED_ENDPOINTS.length);
        for (String endpoint : PARAMETERIZED_ENDPOINTS) {
            for (String payload : XSS_PAYLOADS) {
                String path = endpoint.replace("{id}", urlEncode(payload));
                accessPage(authenticatedClient, path);
            }
        }

        // 4. Test XSS in filter parameters
        log.info("Testing XSS in filter parameters...");
        for (String payload : XSS_PAYLOADS) {
            accessPageWithParams("/transactions", "status=" + urlEncode(payload));
            accessPageWithParams("/transactions", "category=" + urlEncode(payload));
            accessPageWithParams("/templates", "category=" + urlEncode(payload));
            accessPageWithParams("/templates", "tag=" + urlEncode(payload));
            accessPageWithParams("/employees", "department=" + urlEncode(payload));
            accessPageWithParams("/products", "category=" + urlEncode(payload));
            accessPageWithParams("/assets", "category=" + urlEncode(payload));
        }

        // 5. Test XSS in report pages
        log.info("Testing XSS in report pages...");
        for (String report : REPORT_PAGES) {
            for (String payload : XSS_PAYLOADS) {
                accessPageWithParams(report, "search=" + urlEncode(payload));
            }
        }

        waitForPassiveScan();

        if (!QUICK_SCAN) {
            runActiveScan(targetUrl);
        }

        ScanResults results = analyzeAlerts("XSS");
        generateHtmlReport("zap-xss-report.html");

        assertSecurityThresholds(results, "XSS");
    }

    @Test
    @DisplayName("Should pass authentication and IDOR security scan")
    void shouldPassAuthenticationScan() throws Exception {
        log.info("=== Starting Authentication and IDOR Security Scan ===");

        HttpClient unauthClient = createProxiedClient();

        // 1. Test login page
        log.info("Testing login page...");
        accessPage(unauthClient, "/login");

        // 2. Test login with various payloads
        log.info("Testing login with SQLi and XSS payloads...");
        for (String payload : SQL_INJECTION_PAYLOADS) {
            testLoginWithPayload(payload);
        }
        for (String payload : XSS_PAYLOADS) {
            testLoginWithPayload(payload);
        }

        // 3. Test authenticated access
        log.info("Testing authenticated access...");
        authenticatedClient = performLogin("admin", "admin");
        for (String page : LIST_PAGES) {
            accessPage(authenticatedClient, page);
        }

        // 4. Test IDOR on parameterized endpoints
        log.info("Testing IDOR on {} parameterized endpoints...", PARAMETERIZED_ENDPOINTS.length);
        for (String endpoint : PARAMETERIZED_ENDPOINTS) {
            for (String testId : TEST_IDS) {
                String path = endpoint.replace("{id}", testId);
                accessPage(authenticatedClient, path);
            }
        }

        // 5. Test access to admin-only endpoints
        log.info("Testing admin-only endpoints...");
        accessPage(authenticatedClient, "/users");
        accessPage(authenticatedClient, "/settings");
        accessPage(authenticatedClient, "/settings/audit-logs");
        accessPage(authenticatedClient, "/settings/data-subjects");

        // 6. Test logout and access protected pages
        log.info("Testing logout and post-logout access...");
        accessPage(authenticatedClient, "/logout");

        // Try accessing protected pages after logout
        for (String page : LIST_PAGES) {
            accessPage(unauthClient, page);
        }

        waitForPassiveScan();

        if (!QUICK_SCAN) {
            runActiveScan(targetUrl + "/login");
        }

        ScanResults results = analyzeAlerts("Authentication");
        generateHtmlReport("zap-auth-report.html");

        assertSecurityThresholds(results, "Authentication");
    }

    @Test
    @DisplayName("Should pass path traversal and API security scan")
    void shouldPassPathTraversalAndApiScan() throws Exception {
        log.info("=== Starting Path Traversal and API Security Scan ===");

        authenticatedClient = performLogin("admin", "admin");

        // 1. Test path traversal on parameterized endpoints
        log.info("Testing path traversal on {} parameterized endpoints...", PARAMETERIZED_ENDPOINTS.length);
        for (String endpoint : PARAMETERIZED_ENDPOINTS) {
            for (String payload : PATH_TRAVERSAL_PAYLOADS) {
                String path = endpoint.replace("{id}", urlEncode(payload));
                accessPage(authenticatedClient, path);
            }
        }

        // 2. Test path traversal on file-related endpoints
        log.info("Testing path traversal on file endpoints...");
        for (String payload : PATH_TRAVERSAL_PAYLOADS) {
            accessPage(authenticatedClient, "/documents/" + urlEncode(payload) + "/view");
            accessPage(authenticatedClient, "/documents/" + urlEncode(payload) + "/download");
            accessPage(authenticatedClient, "/settings/company/logo");
        }

        // 3. Test API endpoints with payloads
        log.info("Testing {} API endpoints...", API_ENDPOINTS.length);
        for (String endpoint : API_ENDPOINTS) {
            String path = endpoint.replace("{id}", TEST_IDS[0]);
            accessPage(authenticatedClient, path);

            // Test with SQLi payloads
            for (String payload : SQL_INJECTION_PAYLOADS) {
                accessPageWithParams(path, "search=" + urlEncode(payload));
            }
        }

        // 4. Test report export endpoints (potential file disclosure)
        log.info("Testing report export endpoints...");
        String[] exportEndpoints = {
                "/reports/trial-balance/export/pdf",
                "/reports/trial-balance/export/excel",
                "/reports/balance-sheet/export/pdf",
                "/reports/income-statement/export/pdf",
                "/reports/cash-flow/export/pdf",
                "/reports/depreciation/export/pdf",
                "/inventory/reports/stock-balance/export/pdf",
                "/inventory/reports/stock-movement/export/pdf",
                "/inventory/reports/valuation/export/pdf",
                "/inventory/reports/profitability/export/pdf"
        };
        for (String endpoint : exportEndpoints) {
            accessPage(authenticatedClient, endpoint);
        }

        // 5. Test print endpoints
        log.info("Testing print endpoints...");
        String[] printEndpoints = {
                "/reports/trial-balance/print",
                "/reports/balance-sheet/print",
                "/reports/income-statement/print",
                "/reports/cash-flow/print",
                "/reports/ppn-summary/print",
                "/reports/pph23-withholding/print",
                "/reports/depreciation/print",
                "/inventory/reports/stock-balance/print",
                "/inventory/reports/stock-movement/print",
                "/inventory/reports/valuation/print",
                "/inventory/reports/profitability/print"
        };
        for (String endpoint : printEndpoints) {
            accessPage(authenticatedClient, endpoint);
        }

        waitForPassiveScan();

        if (!QUICK_SCAN) {
            runActiveScan(targetUrl);
        }

        ScanResults results = analyzeAlerts("PathTraversalAndAPI");
        generateHtmlReport("zap-path-traversal-api-report.html");

        assertSecurityThresholds(results, "PathTraversalAndAPI");
    }

    // ========== Helper Methods ==========

    private void testLoginWithPayload(String payload) {
        try {
            HttpClient client = createProxiedClient();
            HttpResponse<String> loginPage = getPage(client, "/login");
            String csrf = extractCsrfToken(loginPage.body());

            String formData = "username=" + urlEncode(payload) + "&password=test&_csrf=" + csrf;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl + "/login"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formData))
                    .build();

            client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.debug("Login test error (expected): {}", e.getMessage());
        }
    }

    private void submitFormWithPayloads(String formPath, String postPath, String[] formEndpoint, String[] payloads) {
        try {
            HttpResponse<String> formPage = getPage(authenticatedClient, formPath);
            String csrf = extractCsrfToken(formPage.body());

            if (csrf.isEmpty()) {
                log.debug("No CSRF token found for {}", formPath);
                return;
            }

            // Extract field names from formEndpoint (index 2 onwards)
            for (String payload : payloads) {
                StringBuilder formData = new StringBuilder();
                formData.append("_csrf=").append(csrf);

                for (int i = 2; i < formEndpoint.length; i++) {
                    String fieldName = formEndpoint[i];
                    formData.append("&").append(fieldName).append("=").append(urlEncode(payload));
                }

                postForm(postPath, formData.toString());
            }
        } catch (Exception e) {
            log.debug("Form submission error for {}: {}", formPath, e.getMessage());
        }
    }
}
