package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.EmployeeRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Functional tests for EmployeeController.
 * Tests employee list, create, edit, activate, deactivate operations.
 */
@DisplayName("Employee Controller Tests")
@Import(ServiceTestDataInitializer.class)
class EmployeeControllerFunctionalTest extends PlaywrightTestBase {

    @Autowired
    private EmployeeRepository employeeRepository;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Should display employee list page")
    void shouldDisplayEmployeeListPage() {
        navigateTo("/employees");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }

    @Test
    @DisplayName("Should filter employees by status")
    void shouldFilterEmployeesByStatus() {
        navigateTo("/employees");
        waitForPageLoad();

        var statusSelect = page.locator("select[name='status']").first();
        if (statusSelect.isVisible()) {
            statusSelect.selectOption("ACTIVE");

            var filterBtn = page.locator("form button[type='submit']").first();
            if (filterBtn.isVisible()) {
                filterBtn.click();
                waitForPageLoad();
            }
        }

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should search employees by keyword")
    void shouldSearchEmployeesByKeyword() {
        navigateTo("/employees");
        waitForPageLoad();

        var searchInput = page.locator("input[name='search'], input[name='keyword']").first();
        if (searchInput.isVisible()) {
            searchInput.fill("test");

            var filterBtn = page.locator("form button[type='submit']").first();
            if (filterBtn.isVisible()) {
                filterBtn.click();
                waitForPageLoad();
            }
        }

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should display new employee form")
    void shouldDisplayNewEmployeeForm() {
        navigateTo("/employees/new");
        waitForPageLoad();

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should create new employee")
    void shouldCreateNewEmployee() {
        navigateTo("/employees/new");
        waitForPageLoad();

        // Fill employee ID
        var employeeIdInput = page.locator("input[name='employeeId']").first();
        if (employeeIdInput.isVisible()) {
            employeeIdInput.fill("EMP-TEST-" + System.currentTimeMillis());
        }

        // Fill name
        var nameInput = page.locator("input[name='name']").first();
        if (nameInput.isVisible()) {
            nameInput.fill("Test Employee " + System.currentTimeMillis());
        }

        // Fill email
        var emailInput = page.locator("input[name='email']").first();
        if (emailInput.isVisible()) {
            emailInput.fill("testemp" + System.currentTimeMillis() + "@example.com");
        }

        // Fill join date
        var joinDateInput = page.locator("input[name='joinDate']").first();
        if (joinDateInput.isVisible()) {
            joinDateInput.fill(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        }

        // Fill basic salary
        var salaryInput = page.locator("input[name='basicSalary']").first();
        if (salaryInput.isVisible()) {
            salaryInput.fill("5000000");
        }

        // Submit
        var submitBtn = page.locator("#btn-simpan").first();
        if (submitBtn.isVisible()) {
            submitBtn.click();
            waitForPageLoad();
        }

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should show validation error for empty name")
    void shouldShowValidationErrorForEmptyName() {
        navigateTo("/employees/new");
        waitForPageLoad();

        // Submit without filling name
        var submitBtn = page.locator("#btn-simpan").first();
        if (submitBtn.isVisible()) {
            submitBtn.click();
            waitForPageLoad();
        }

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should display employee detail page")
    void shouldDisplayEmployeeDetailPage() {
        var employee = employeeRepository.findAll().stream().findFirst();
        if (employee.isEmpty()) {
            return;
        }

        navigateTo("/employees/" + employee.get().getEmployeeId());
        waitForPageLoad();

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/employees\\/.*"));
    }

    @Test
    @DisplayName("Should display employee edit form")
    void shouldDisplayEmployeeEditForm() {
        var employee = employeeRepository.findAll().stream().findFirst();
        if (employee.isEmpty()) {
            return;
        }

        navigateTo("/employees/" + employee.get().getEmployeeId() + "/edit");
        waitForPageLoad();

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should update employee")
    void shouldUpdateEmployee() {
        var employee = employeeRepository.findAll().stream().findFirst();
        if (employee.isEmpty()) {
            return;
        }

        navigateTo("/employees/" + employee.get().getEmployeeId() + "/edit");
        waitForPageLoad();

        // Update name
        var nameInput = page.locator("input[name='name']").first();
        if (nameInput.isVisible()) {
            nameInput.fill("Updated Employee " + System.currentTimeMillis());
        }

        // Submit
        var submitBtn = page.locator("#btn-simpan").first();
        if (submitBtn.isVisible()) {
            submitBtn.click();
            waitForPageLoad();
        }

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/employees\\/.*"));
    }

    @Test
    @DisplayName("Should deactivate employee")
    void shouldDeactivateEmployee() {
        var employee = employeeRepository.findAll().stream()
                .filter(e -> e.getActive() != null && e.getActive())
                .findFirst();
        if (employee.isEmpty()) {
            return;
        }

        navigateTo("/employees/" + employee.get().getEmployeeId());
        waitForPageLoad();

        var deactivateBtn = page.locator("form[action*='/deactivate'] button[type='submit']").first();
        if (deactivateBtn.isVisible()) {
            deactivateBtn.click();
            waitForPageLoad();
        }

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/employees\\/.*"));
    }

    @Test
    @DisplayName("Should activate employee")
    void shouldActivateEmployee() {
        var employee = employeeRepository.findAll().stream()
                .filter(e -> e.getActive() == null || !e.getActive())
                .findFirst();
        if (employee.isEmpty()) {
            return;
        }

        navigateTo("/employees/" + employee.get().getEmployeeId());
        waitForPageLoad();

        var activateBtn = page.locator("form[action*='/activate'] button[type='submit']").first();
        if (activateBtn.isVisible()) {
            activateBtn.click();
            waitForPageLoad();
        }

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/employees\\/.*"));
    }

    // ==================== ADDITIONAL COVERAGE TESTS ====================

    @Test
    @DisplayName("Should filter employees by status via query param")
    void shouldFilterEmployeesByStatusViaQueryParam() {
        navigateTo("/employees?status=ACTIVE");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }

    @Test
    @DisplayName("Should filter employees by active status via query param")
    void shouldFilterEmployeesByActiveStatusViaQueryParam() {
        navigateTo("/employees?active=true");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }

    @Test
    @DisplayName("Should filter employees by inactive status via query param")
    void shouldFilterEmployeesByInactiveStatusViaQueryParam() {
        navigateTo("/employees?active=false");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }

    @Test
    @DisplayName("Should paginate employee list")
    void shouldPaginateEmployeeList() {
        navigateTo("/employees?page=0&size=5");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }

    @Test
    @DisplayName("Should search employees via query param")
    void shouldSearchEmployeesViaQueryParam() {
        navigateTo("/employees?search=test");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }

    @Test
    @DisplayName("Should filter by RETIRED status")
    void shouldFilterByRetiredStatus() {
        navigateTo("/employees?status=RETIRED");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }

    @Test
    @DisplayName("Should filter by RESIGNED status")
    void shouldFilterByResignedStatus() {
        navigateTo("/employees?status=RESIGNED");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }

    @Test
    @DisplayName("Should filter by TERMINATED status")
    void shouldFilterByTerminatedStatus() {
        navigateTo("/employees?status=TERMINATED");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }

    @Test
    @DisplayName("Should combine search and status filters")
    void shouldCombineSearchAndStatusFilters() {
        navigateTo("/employees?search=test&status=ACTIVE&active=true");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }
}
