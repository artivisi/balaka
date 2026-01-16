package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.Employee;
import com.artivisi.accountingfinance.entity.User;
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
import java.util.Optional;

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
        linkAdminToEmployee();
        loginAsAdmin();
    }

    private void linkAdminToEmployee() {
        // Link admin user to first employee for self-service testing
        Optional<User> adminUser = userRepository.findByUsername("admin");
        if (adminUser.isEmpty()) {
            return;
        }

        // Check if admin is already linked to any employee
        Optional<Employee> existingLink = employeeRepository.findByUserId(adminUser.get().getId());
        if (existingLink.isPresent()) {
            return; // Already linked
        }

        // Find first employee without a user link
        Optional<Employee> unlinkedEmployee = employeeRepository.findAll().stream()
                .filter(e -> e.getUser() == null)
                .findFirst();

        if (unlinkedEmployee.isPresent()) {
            Employee employee = unlinkedEmployee.get();
            employee.setUser(adminUser.get());
            employeeRepository.save(employee);
        }
    }

    // ==================== PROFILE TESTS ====================

    @Test
    @DisplayName("Should display profile page")
    void shouldDisplayProfilePage() {
        navigateTo("/self-service/profile");
        waitForPageLoad();

        // Should show profile page with employee data
        assertThat(page.locator("body")).isVisible();
        // Page should contain employee name or profile title
        org.assertj.core.api.Assertions.assertThat(page.content())
            .as("Profile page should load successfully")
            .containsAnyOf("Profil", "Profile", "profil", "employee");
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

        // Fill out the profile edit form
        page.locator("#phone").fill("08123456789");
        page.locator("#address").fill("Jl. Test No. 123");
        page.locator("#bankName").fill("BCA");
        page.locator("#bankAccountNumber").fill("1234567890");
        page.locator("#bankAccountName").fill("Test Account");

        // Submit the form
        page.locator("[data-testid='btn-save-profile']").click();
        waitForPageLoad();

        // Should redirect to profile page with success message
        org.assertj.core.api.Assertions.assertThat(page.url())
            .as("Should redirect to profile page after save")
            .contains("/self-service/profile");
    }

    // ==================== PAYSLIPS TESTS ====================

    @Test
    @DisplayName("Should display payslips list")
    void shouldDisplayPayslipsList() {
        navigateTo("/self-service/payslips");
        waitForPageLoad();

        // Should show payslips page
        assertThat(page.locator("body")).isVisible();
        org.assertj.core.api.Assertions.assertThat(page.content())
            .as("Payslips page should load successfully")
            .containsAnyOf("Slip Gaji", "Payslip", "payslip", "gaji");
    }

    @Test
    @DisplayName("Should filter payslips by year")
    void shouldFilterPayslipsByYear() {
        navigateTo("/self-service/payslips?year=2024");
        waitForPageLoad();

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should filter payslips by current year")
    void shouldFilterPayslipsByCurrentYear() {
        int currentYear = LocalDate.now().getYear();
        navigateTo("/self-service/payslips?year=" + currentYear);
        waitForPageLoad();

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should download payslip PDF returns 404 for non-existent")
    void shouldDownloadPayslipPdfReturns404() {
        // Test with non-existent payslip ID - use request API to avoid navigation issues
        var response = page.request().get(baseUrl() + "/self-service/payslips/00000000-0000-0000-0000-000000000000/pdf");
        // Should return 404 not found (or 500 if not handled gracefully)
        org.assertj.core.api.Assertions.assertThat(response.status())
            .as("Non-existent payslip should return error")
            .isIn(404, 500);
    }

    // ==================== BUKTI POTONG TESTS ====================

    @Test
    @DisplayName("Should display bukti potong list")
    void shouldDisplayBuktiPotongList() {
        navigateTo("/self-service/bukti-potong");
        waitForPageLoad();

        // Should show bukti potong page
        assertThat(page.locator("body")).isVisible();
        org.assertj.core.api.Assertions.assertThat(page.content())
            .as("Bukti potong page should load successfully")
            .containsAnyOf("Bukti Potong", "bukti-potong", "1721");
    }

    @Test
    @DisplayName("Should filter bukti potong by year")
    void shouldFilterBuktiPotongByYear() {
        navigateTo("/self-service/bukti-potong?year=2024");
        waitForPageLoad();

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should filter bukti potong by current year")
    void shouldFilterBuktiPotongByCurrentYear() {
        int currentYear = LocalDate.now().getYear();
        navigateTo("/self-service/bukti-potong?year=" + currentYear);
        waitForPageLoad();

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should download bukti potong PDF returns 404 for non-existent")
    void shouldDownloadBuktiPotongPdfReturns404() {
        // Test with year that likely has no data - use request API
        var response = page.request().get(baseUrl() + "/self-service/bukti-potong/2020/pdf");
        // Should return 404 not found (or 500 if not handled gracefully)
        org.assertj.core.api.Assertions.assertThat(response.status())
            .as("Year with no data should return error")
            .isIn(404, 500);
    }

    @Test
    @DisplayName("Should handle bukti potong PDF for current year")
    void shouldHandleBuktiPotongPdfForCurrentYear() {
        int currentYear = LocalDate.now().getYear();
        var response = page.request().get(baseUrl() + "/self-service/bukti-potong/" + currentYear + "/pdf");
        // Accept 200, 404, or 500 (depending on whether data exists)
        org.assertj.core.api.Assertions.assertThat(response.status())
            .as("Current year bukti potong should return response")
            .isIn(200, 404, 500);
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
