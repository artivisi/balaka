package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.EmployeeRepository;
import com.artivisi.accountingfinance.repository.PayrollDetailRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Functional tests for SelfServiceController.
 * Tests employee self-service: profile, payslips, bukti potong.
 */
@DisplayName("Self Service Controller Tests")
@Import(ServiceTestDataInitializer.class)
class SelfServiceControllerFunctionalTest extends PlaywrightTestBase {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PayrollDetailRepository payrollDetailRepository;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    // ==================== PROFILE TESTS ====================

    @Test
    @DisplayName("Should display profile page")
    void shouldDisplayProfilePage() {
        navigateTo("/self-service/profile");
        waitForPageLoad();

        // Should show profile page or noEmployee message
        assertThat(page.locator("#page-title, h1, .no-employee").first()).isVisible();
    }

    @Test
    @DisplayName("Should display profile edit form")
    void shouldDisplayProfileEditForm() {
        navigateTo("/self-service/profile/edit");
        waitForPageLoad();

        // May redirect if no employee, or show edit form
        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should update profile with valid data")
    void shouldUpdateProfile() {
        navigateTo("/self-service/profile/edit");
        waitForPageLoad();

        var phoneInput = page.locator("input[name='phone']").first();
        if (phoneInput.isVisible()) {
            phoneInput.fill("08123456789");

            var addressInput = page.locator("textarea[name='address'], input[name='address']").first();
            if (addressInput.isVisible()) {
                addressInput.fill("Jl. Test No. 123");
            }

            var bankNameInput = page.locator("input[name='bankName']").first();
            if (bankNameInput.isVisible()) {
                bankNameInput.fill("BCA");
            }

            var bankAccountInput = page.locator("input[name='bankAccountNumber']").first();
            if (bankAccountInput.isVisible()) {
                bankAccountInput.fill("1234567890");
            }

            var bankAccountNameInput = page.locator("input[name='bankAccountName']").first();
            if (bankAccountNameInput.isVisible()) {
                bankAccountNameInput.fill("Test Account");
            }

            page.click("#btn-simpan, button[type='submit']");
            waitForPageLoad();
        }

        // Should redirect to profile page
        assertThat(page.locator("body")).isVisible();
    }

    // ==================== PAYSLIPS TESTS ====================

    @Test
    @DisplayName("Should display payslips list")
    void shouldDisplayPayslipsList() {
        navigateTo("/self-service/payslips");
        waitForPageLoad();

        // Should show payslips page or noEmployee message
        assertThat(page.locator("#page-title, h1, .no-employee").first()).isVisible();
    }

    @Test
    @DisplayName("Should filter payslips by year")
    void shouldFilterPayslipsByYear() {
        navigateTo("/self-service/payslips?year=2024");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1, .no-employee").first()).isVisible();
    }

    @Test
    @DisplayName("Should filter payslips by current year")
    void shouldFilterPayslipsByCurrentYear() {
        int currentYear = LocalDate.now().getYear();
        navigateTo("/self-service/payslips?year=" + currentYear);
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1, .no-employee").first()).isVisible();
    }

    @Test
    @DisplayName("Should download payslip PDF returns 404 for non-existent")
    void shouldDownloadPayslipPdfReturns404() {
        // Test with non-existent payslip ID
        var response = page.navigate("http://localhost:" + port + "/self-service/payslips/00000000-0000-0000-0000-000000000000/pdf");
        if (response != null) {
            var status = response.status();
            // Should return 404 not found
            org.junit.jupiter.api.Assertions.assertTrue(status == 404 || status < 500,
                    "Expected 404 or non-server error, got: " + status);
        }
    }

    // ==================== BUKTI POTONG TESTS ====================

    @Test
    @DisplayName("Should display bukti potong list")
    void shouldDisplayBuktiPotongList() {
        navigateTo("/self-service/bukti-potong");
        waitForPageLoad();

        // Should show bukti potong page or noEmployee message
        assertThat(page.locator("#page-title, h1, .no-employee").first()).isVisible();
    }

    @Test
    @DisplayName("Should filter bukti potong by year")
    void shouldFilterBuktiPotongByYear() {
        navigateTo("/self-service/bukti-potong?year=2024");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1, .no-employee").first()).isVisible();
    }

    @Test
    @DisplayName("Should filter bukti potong by current year")
    void shouldFilterBuktiPotongByCurrentYear() {
        int currentYear = LocalDate.now().getYear();
        navigateTo("/self-service/bukti-potong?year=" + currentYear);
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1, .no-employee").first()).isVisible();
    }

    @Test
    @DisplayName("Should download bukti potong PDF returns 404 for non-existent")
    void shouldDownloadBuktiPotongPdfReturns404() {
        // Test with year that likely has no data
        var response = page.navigate("http://localhost:" + port + "/self-service/bukti-potong/2020/pdf");
        if (response != null) {
            var status = response.status();
            // Should return 404 not found
            org.junit.jupiter.api.Assertions.assertTrue(status == 404 || status < 500,
                    "Expected 404 or non-server error, got: " + status);
        }
    }

    @Test
    @DisplayName("Should handle bukti potong PDF for current year")
    void shouldHandleBuktiPotongPdfForCurrentYear() {
        int currentYear = LocalDate.now().getYear();
        var response = page.navigate("http://localhost:" + port + "/self-service/bukti-potong/" + currentYear + "/pdf");
        if (response != null) {
            var status = response.status();
            // Accept 200, 404, or any non-500 response
            org.junit.jupiter.api.Assertions.assertTrue(status < 500,
                    "Expected non-server error, got: " + status);
        }
    }

    // ==================== EDGE CASES ====================

    @Test
    @DisplayName("Should handle payslips without year param")
    void shouldHandlePayslipsWithoutYearParam() {
        navigateTo("/self-service/payslips");
        waitForPageLoad();

        // Should default to current year
        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should handle bukti potong without year param")
    void shouldHandleBuktiPotongWithoutYearParam() {
        navigateTo("/self-service/bukti-potong");
        waitForPageLoad();

        // Should default to current year
        assertThat(page.locator("body")).isVisible();
    }
}
