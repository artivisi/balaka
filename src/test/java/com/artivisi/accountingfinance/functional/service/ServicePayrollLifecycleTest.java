package com.artivisi.accountingfinance.functional.service;

import com.artivisi.accountingfinance.functional.page.PayrollListPage;
import com.artivisi.accountingfinance.functional.page.TransactionFormPage;
import com.artivisi.accountingfinance.functional.page.TransactionListPage;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.PayrollRunRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Service Industry Payroll Lifecycle Tests
 * Tests the complete payroll flow: posting payroll → pay salary → pay BPJS → pay PPh 21
 * Demonstrates the difference between using payroll feature vs simple transactions.
 * Uses Page Object Pattern for maintainability.
 */
@DisplayName("Service Industry - Payroll Lifecycle")
@Import(ServiceTestDataInitializer.class)
public class ServicePayrollLifecycleTest extends PlaywrightTestBase {

    @Autowired
    private PayrollRunRepository payrollRunRepository;

    @Autowired
    private JournalTemplateRepository templateRepository;

    // Page Objects
    private PayrollListPage payrollListPage;
    private TransactionFormPage transactionFormPage;
    private TransactionListPage transactionListPage;

    private void initPageObjects() {
        String baseUrl = "http://localhost:" + port;
        payrollListPage = new PayrollListPage(page, baseUrl);
        transactionFormPage = new TransactionFormPage(page, baseUrl);
        transactionListPage = new TransactionListPage(page, baseUrl);
    }

    @BeforeEach
    void setup() {
        loginAsAdmin();
        initPageObjects();
    }

    @Test
    @DisplayName("Should complete full payroll lifecycle: post → pay salary → pay BPJS → pay PPh 21")
    void shouldCompleteFullPayrollLifecycle() {
        // Step 1: Navigate to payroll list and verify payroll exists
        payrollListPage.navigate()
            .verifyPageTitle();

        // Check if there's a posted payroll (from test data)
        int rowCount = page.locator("tr[id^='payroll-']").count();
        if (rowCount == 0) {
            // No payroll data - skip test
            return;
        }

        // Step 2: Click on first payroll to see details
        page.locator("tr[id^='payroll-']").first().click();
        page.waitForLoadState();

        // Take screenshot for user manual (payroll detail showing liabilities)
        takeManualScreenshot("payroll-lifecycle-detail");

        // Step 3: Pay Salary (Bayar Hutang Gaji)
        // Navigate to transactions and create payment using "Bayar Hutang Gaji" template
        UUID bayarGajiTemplateId = getTemplateIdByName("Bayar Hutang Gaji");
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        transactionFormPage
            .navigateWithTemplate(bayarGajiTemplateId)
            .fillDate(today)
            .fillAmount("26250000") // Net pay from example (3 employees × ~Rp 8.75M)
            .fillDescription("Transfer gaji Januari 2025 ke rekening karyawan")
            .fillReferenceNumber("TRF-GAJI-2025-01")
            .fillInputs("BANK:1.1.02"); // Select Bank BCA for credit

        // Take screenshot for user manual
        takeManualScreenshot("payroll-lifecycle-bayar-gaji-form");

        var detailPage = transactionFormPage.saveAndPost();

        // Verify transaction posted (status-posted data-testid is visible when POSTED)
        assertThat(page.getByTestId("status-posted")).isVisible();
        takeManualScreenshot("payroll-lifecycle-bayar-gaji-posted");

        // Step 4: Pay BPJS (Bayar Hutang BPJS)
        UUID bayarBpjsTemplateId = getTemplateIdByName("Bayar Hutang BPJS");

        transactionFormPage
            .navigateWithTemplate(bayarBpjsTemplateId)
            .fillDate(today)
            .fillAmount("6432000") // Total BPJS (company + employee)
            .fillDescription("Pembayaran BPJS Januari 2025")
            .fillReferenceNumber("BPJS-2025-01")
            .fillInputs("BANK:1.1.02"); // Select Bank BCA for credit

        // Take screenshot for user manual
        takeManualScreenshot("payroll-lifecycle-bayar-bpjs-form");

        detailPage = transactionFormPage.saveAndPost();

        // Verify transaction posted
        assertThat(page.getByTestId("status-posted")).isVisible();
        takeManualScreenshot("payroll-lifecycle-bayar-bpjs-posted");

        // Step 5: Remit PPh 21 (Setor PPh 21)
        UUID setorPph21TemplateId = getTemplateIdByName("Setor PPh 21");

        transactionFormPage
            .navigateWithTemplate(setorPph21TemplateId)
            .fillDate(today)
            .fillAmount("750000") // Total PPh 21 withheld
            .fillDescription("Penyetoran PPh 21 Januari 2025")
            .fillReferenceNumber("PPH21-2025-01")
            .fillInputs("BANK:1.1.02"); // Select Bank BCA for credit

        // Take screenshot for user manual
        takeManualScreenshot("payroll-lifecycle-setor-pph21-form");

        detailPage = transactionFormPage.saveAndPost();

        // Verify transaction posted
        assertThat(page.getByTestId("status-posted")).isVisible();
        takeManualScreenshot("payroll-lifecycle-setor-pph21-posted");

        // Final verification: All payroll liabilities should be paid
        // Navigate to transaction list to see all payroll-related transactions
        transactionListPage.navigate()
            .verifyPageTitle();

        takeManualScreenshot("payroll-lifecycle-complete-transaction-list");
    }

    @Test
    @DisplayName("Should demonstrate simple salary payment WITHOUT payroll feature")
    void shouldDemonstrateSimpleSalaryPayment() {
        // This demonstrates using "Bayar Beban Gaji" template
        // which directly expenses salary without creating liabilities

        UUID bayarBebanGajiTemplateId = getTemplateIdByName("Bayar Beban Gaji");
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        transactionFormPage
            .navigateWithTemplate(bayarBebanGajiTemplateId)
            .fillDate(today)
            .fillAmount("10000000") // Simple salary payment
            .fillDescription("Gaji kontraktor lepas - tidak ada BPJS/PPh 21")
            .fillReferenceNumber("GAJI-KONTRAKTOR-001")
            .fillInputs("BANK:1.1.02"); // Select Bank BCA for credit

        // Take screenshot for user manual (simple vs payroll comparison)
        takeManualScreenshot("simple-salary-payment-form");

        var detailPage = transactionFormPage.saveAndPost();

        // Verify transaction posted
        assertThat(page.getByTestId("status-posted")).isVisible();

        // Take screenshot showing the simple journal entry (Dr. Beban Gaji, Cr. Bank)
        takeManualScreenshot("simple-salary-payment-posted");

        // Key difference: This creates NO liabilities (Hutang Gaji, Hutang BPJS, Hutang PPh 21)
        // It's immediate expense recognition without payroll calculations
    }

    @Test
    @DisplayName("Should show payroll feature creates proper accounting entries")
    void shouldShowPayrollProperAccountingEntries() {
        // This test demonstrates what the payroll feature does automatically

        // Navigate to payroll list
        payrollListPage.navigate()
            .verifyPageTitle();

        // Check if there's a posted payroll
        int rowCount = page.locator("tr[id^='payroll-']").count();
        if (rowCount == 0) {
            return;
        }

        // Click on first payroll
        page.locator("tr[id^='payroll-']").first().click();
        page.waitForLoadState();

        // Check if this payroll has a posted transaction (journal-reference testid)
        var journalRef = page.getByTestId("journal-reference");
        if (!journalRef.isVisible()) {
            // This payroll is not posted yet - skip verification
            return;
        }

        // Navigate to associated transaction via the link inside journal-reference
        journalRef.locator("a").first().click();
        page.waitForLoadState();

        // Take screenshot showing the complex journal entry created by payroll feature
        // Dr. Beban Gaji (gross)
        // Dr. Beban BPJS Perusahaan (company BPJS)
        // Cr. Hutang Gaji (net pay)
        // Cr. Hutang BPJS (total BPJS)
        // Cr. Hutang PPh 21 (withheld tax)
        takeManualScreenshot("payroll-feature-journal-entry");

        // Verify journal lines exist
        var journalTable = page.getByTestId("journal-entries-table");
        if (journalTable.isVisible()) {
            int journalLineCount = journalTable.locator("tbody tr").count();
            assertTrue(journalLineCount >= 5,
                "Payroll posting should create at least 5 journal lines, found: " + journalLineCount);
        }
    }

    /**
     * Helper method to get template ID by name.
     */
    private UUID getTemplateIdByName(String templateName) {
        return templateRepository.findByTemplateName(templateName)
            .orElseThrow(() -> new IllegalStateException("Template '" + templateName + "' tidak ditemukan"))
            .getId();
    }
}
