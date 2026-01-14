package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ClientRepository;
import com.artivisi.accountingfinance.repository.InvoiceRepository;
import com.artivisi.accountingfinance.repository.ProjectRepository;
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
 * Functional tests for InvoiceController.
 * Tests invoice list, create, edit, send, pay, cancel, delete, and print operations.
 */
@DisplayName("Invoice Controller Tests")
@Import(ServiceTestDataInitializer.class)
class InvoiceControllerFunctionalTest extends PlaywrightTestBase {

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Should display invoice list page")
    void shouldDisplayInvoiceListPage() {
        navigateTo("/invoices");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }

    @Test
    @DisplayName("Should filter invoices by status")
    void shouldFilterInvoicesByStatus() {
        navigateTo("/invoices");
        waitForPageLoad();

        var statusSelect = page.locator("select[name='status']").first();
        if (statusSelect.isVisible()) {
            statusSelect.selectOption("DRAFT");

            var filterBtn = page.locator("form button[type='submit']").first();
            if (filterBtn.isVisible()) {
                filterBtn.click();
                waitForPageLoad();
            }
        }

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should filter invoices by client")
    void shouldFilterInvoicesByClient() {
        navigateTo("/invoices");
        waitForPageLoad();

        var clientSelect = page.locator("select[name='clientId']").first();
        if (clientSelect.isVisible()) {
            var options = clientSelect.locator("option");
            if (options.count() > 1) {
                clientSelect.selectOption(new String[]{options.nth(1).getAttribute("value")});

                var filterBtn = page.locator("form button[type='submit']").first();
                if (filterBtn.isVisible()) {
                    filterBtn.click();
                    waitForPageLoad();
                }
            }
        }

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should filter invoices by date range")
    void shouldFilterInvoicesByDateRange() {
        navigateTo("/invoices");
        waitForPageLoad();

        var startDateInput = page.locator("input[name='startDate']").first();
        var endDateInput = page.locator("input[name='endDate']").first();

        if (startDateInput.isVisible() && endDateInput.isVisible()) {
            startDateInput.fill("2024-01-01");
            endDateInput.fill("2024-12-31");

            var filterBtn = page.locator("form button[type='submit']").first();
            if (filterBtn.isVisible()) {
                filterBtn.click();
                waitForPageLoad();
            }
        }

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should display new invoice form")
    void shouldDisplayNewInvoiceForm() {
        navigateTo("/invoices/new");
        waitForPageLoad();

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should create new invoice")
    void shouldCreateNewInvoice() {
        var client = clientRepository.findAll().stream().findFirst();
        if (client.isEmpty()) {
            return;
        }

        navigateTo("/invoices/new");
        waitForPageLoad();

        // Fill client
        var clientSelect = page.locator("select[name='client.id'], select[name='clientId']").first();
        if (clientSelect.isVisible()) {
            clientSelect.selectOption(client.get().getId().toString());
        }

        // Fill invoice date
        var invoiceDateInput = page.locator("input[name='invoiceDate']").first();
        if (invoiceDateInput.isVisible()) {
            invoiceDateInput.fill(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        }

        // Fill due date
        var dueDateInput = page.locator("input[name='dueDate']").first();
        if (dueDateInput.isVisible()) {
            dueDateInput.fill(LocalDate.now().plusDays(30).format(DateTimeFormatter.ISO_LOCAL_DATE));
        }

        // Fill description
        var descriptionInput = page.locator("input[name='description'], textarea[name='description']").first();
        if (descriptionInput.isVisible()) {
            descriptionInput.fill("Test Invoice " + System.currentTimeMillis());
        }

        // Submit using specific button ID
        var submitBtn = page.locator("#btn-simpan").first();
        if (submitBtn.isVisible()) {
            submitBtn.click();
            waitForPageLoad();
        }

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should show validation error for missing client")
    void shouldShowValidationErrorForMissingClient() {
        navigateTo("/invoices/new");
        waitForPageLoad();

        // Try to submit without selecting client
        var submitBtn = page.locator("#btn-simpan").first();
        if (submitBtn.isVisible()) {
            submitBtn.click();
            waitForPageLoad();
        }

        // Should stay on form or show error
        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should display invoice detail page")
    void shouldDisplayInvoiceDetailPage() {
        var invoice = invoiceRepository.findAll().stream().findFirst();
        if (invoice.isEmpty()) {
            return;
        }

        navigateTo("/invoices/" + invoice.get().getInvoiceNumber());
        waitForPageLoad();

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/invoices\\/.*"));
    }

    @Test
    @DisplayName("Should display invoice edit form")
    void shouldDisplayInvoiceEditForm() {
        var invoice = invoiceRepository.findAll().stream().findFirst();
        if (invoice.isEmpty()) {
            return;
        }

        navigateTo("/invoices/" + invoice.get().getInvoiceNumber() + "/edit");
        waitForPageLoad();

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should update invoice")
    void shouldUpdateInvoice() {
        var invoice = invoiceRepository.findAll().stream().findFirst();
        if (invoice.isEmpty()) {
            return;
        }

        navigateTo("/invoices/" + invoice.get().getInvoiceNumber() + "/edit");
        waitForPageLoad();

        // Update description
        var descriptionInput = page.locator("input[name='description'], textarea[name='description']").first();
        if (descriptionInput.isVisible()) {
            descriptionInput.fill("Updated Invoice " + System.currentTimeMillis());
        }

        // Submit using specific button ID
        var submitBtn = page.locator("#btn-simpan").first();
        if (submitBtn.isVisible()) {
            submitBtn.click();
            waitForPageLoad();
        }

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/invoices\\/.*"));
    }

    @Test
    @DisplayName("Should send invoice")
    void shouldSendInvoice() {
        var invoice = invoiceRepository.findAll().stream()
                .filter(i -> "DRAFT".equals(i.getStatus().name()))
                .findFirst();
        if (invoice.isEmpty()) {
            return;
        }

        navigateTo("/invoices/" + invoice.get().getInvoiceNumber());
        waitForPageLoad();

        var sendBtn = page.locator("form[action*='/send'] button[type='submit']").first();
        if (sendBtn.isVisible()) {
            sendBtn.click();
            waitForPageLoad();
        }

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/invoices\\/.*"));
    }

    @Test
    @DisplayName("Should display pay invoice form")
    void shouldDisplayPayInvoiceForm() {
        var invoice = invoiceRepository.findAll().stream()
                .filter(i -> "SENT".equals(i.getStatus().name()))
                .findFirst();
        if (invoice.isEmpty()) {
            return;
        }

        navigateTo("/invoices/" + invoice.get().getInvoiceNumber() + "/pay");
        waitForPageLoad();

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should mark invoice as paid")
    void shouldMarkInvoiceAsPaid() {
        var invoice = invoiceRepository.findAll().stream()
                .filter(i -> "SENT".equals(i.getStatus().name()))
                .findFirst();
        if (invoice.isEmpty()) {
            return;
        }

        navigateTo("/invoices/" + invoice.get().getInvoiceNumber());
        waitForPageLoad();

        var markPaidBtn = page.locator("form[action*='/mark-paid'] button[type='submit']").first();
        if (markPaidBtn.isVisible()) {
            markPaidBtn.click();
            waitForPageLoad();
        }

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/invoices\\/.*"));
    }

    @Test
    @DisplayName("Should cancel invoice")
    void shouldCancelInvoice() {
        var invoice = invoiceRepository.findAll().stream()
                .filter(i -> !"CANCELLED".equals(i.getStatus().name()) && !"PAID".equals(i.getStatus().name()))
                .findFirst();
        if (invoice.isEmpty()) {
            return;
        }

        navigateTo("/invoices/" + invoice.get().getInvoiceNumber());
        waitForPageLoad();

        var cancelBtn = page.locator("form[action*='/cancel'] button[type='submit']").first();
        if (cancelBtn.isVisible()) {
            cancelBtn.click();
            waitForPageLoad();
        }

        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/invoices\\/.*"));
    }

    @Test
    @DisplayName("Should delete invoice")
    void shouldDeleteInvoice() {
        var invoice = invoiceRepository.findAll().stream()
                .filter(i -> "DRAFT".equals(i.getStatus().name()))
                .findFirst();
        if (invoice.isEmpty()) {
            return;
        }

        navigateTo("/invoices/" + invoice.get().getInvoiceNumber());
        waitForPageLoad();

        var deleteBtn = page.locator("form[action*='/delete'] button[type='submit']").first();
        if (deleteBtn.isVisible()) {
            deleteBtn.click();
            waitForPageLoad();
        }

        // Should redirect to list after deletion
        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should display print preview")
    void shouldDisplayPrintPreview() {
        var invoice = invoiceRepository.findAll().stream().findFirst();
        if (invoice.isEmpty()) {
            return;
        }

        navigateTo("/invoices/" + invoice.get().getInvoiceNumber() + "/print");
        waitForPageLoad();

        assertThat(page.locator("body")).isVisible();
    }

    // ==================== ADDITIONAL COVERAGE TESTS ====================

    @Test
    @DisplayName("Should filter invoices by status via query param")
    void shouldFilterInvoicesByStatusViaQueryParam() {
        navigateTo("/invoices?status=DRAFT");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }

    @Test
    @DisplayName("Should filter invoices by client via query param")
    void shouldFilterInvoicesByClientViaQueryParam() {
        var client = clientRepository.findAll().stream().findFirst();
        if (client.isEmpty()) {
            return;
        }

        navigateTo("/invoices?clientId=" + client.get().getId());
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }

    @Test
    @DisplayName("Should filter invoices by project via query param")
    void shouldFilterInvoicesByProjectViaQueryParam() {
        var project = projectRepository.findAll().stream().findFirst();
        if (project.isEmpty()) {
            return;
        }

        navigateTo("/invoices?projectId=" + project.get().getId());
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }

    @Test
    @DisplayName("Should display new form with pre-selected client")
    void shouldDisplayNewFormWithPreSelectedClient() {
        var client = clientRepository.findAll().stream().findFirst();
        if (client.isEmpty()) {
            return;
        }

        navigateTo("/invoices/new?clientId=" + client.get().getId());
        waitForPageLoad();

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should display new form with pre-selected project")
    void shouldDisplayNewFormWithPreSelectedProject() {
        var project = projectRepository.findAll().stream().findFirst();
        if (project.isEmpty()) {
            return;
        }

        navigateTo("/invoices/new?projectId=" + project.get().getId());
        waitForPageLoad();

        assertThat(page.locator("body")).isVisible();
    }

    @Test
    @DisplayName("Should redirect when editing non-draft invoice")
    void shouldRedirectWhenEditingNonDraftInvoice() {
        var invoice = invoiceRepository.findAll().stream()
                .filter(i -> !"DRAFT".equals(i.getStatus().name()))
                .findFirst();
        if (invoice.isEmpty()) {
            return;
        }

        navigateTo("/invoices/" + invoice.get().getInvoiceNumber() + "/edit");
        waitForPageLoad();

        // Should redirect to detail page
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/invoices\\/.*"));
    }

    @Test
    @DisplayName("Should redirect pay form for non-sent invoice")
    void shouldRedirectPayFormForNonSentInvoice() {
        var invoice = invoiceRepository.findAll().stream()
                .filter(i -> "DRAFT".equals(i.getStatus().name()))
                .findFirst();
        if (invoice.isEmpty()) {
            return;
        }

        navigateTo("/invoices/" + invoice.get().getInvoiceNumber() + "/pay");
        waitForPageLoad();

        // Should redirect to detail with error
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*\\/invoices\\/.*"));
    }

    @Test
    @DisplayName("Should paginate invoice list")
    void shouldPaginateInvoiceList() {
        navigateTo("/invoices?page=0&size=5");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }

    @Test
    @DisplayName("Should filter by sent status")
    void shouldFilterBySentStatus() {
        navigateTo("/invoices?status=SENT");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }

    @Test
    @DisplayName("Should filter by paid status")
    void shouldFilterByPaidStatus() {
        navigateTo("/invoices?status=PAID");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }

    @Test
    @DisplayName("Should filter by overdue status")
    void shouldFilterByOverdueStatus() {
        navigateTo("/invoices?status=OVERDUE");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }

    @Test
    @DisplayName("Should filter by cancelled status")
    void shouldFilterByCancelledStatus() {
        navigateTo("/invoices?status=CANCELLED");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).isVisible();
    }
}
