package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.RecurringTransaction;
import com.artivisi.accountingfinance.enums.RecurringFrequency;
import com.artivisi.accountingfinance.enums.RecurringStatus;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.RecurringTransactionRepository;
import com.artivisi.accountingfinance.service.JournalTemplateService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@DisplayName("Recurring Transaction Tests")
@Import(ServiceTestDataInitializer.class)
class RecurringTransactionTest extends PlaywrightTestBase {

    @Autowired
    private RecurringTransactionRepository recurringTransactionRepository;

    @Autowired
    private JournalTemplateService journalTemplateService;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Should display recurring list page with empty state")
    void shouldDisplayRecurringListPage() {
        navigateTo("/recurring");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).containsText("Transaksi Berulang");
        assertThat(page.locator("[data-testid='recurring-table']")).isVisible();
    }

    @Test
    @DisplayName("Should navigate to recurring from sidebar")
    void shouldNavigateFromSidebar() {
        navigateTo("/dashboard");
        waitForPageLoad();

        // Akuntansi group is open by default, so nav-recurring should be visible
        var recurringLink = page.locator("#nav-recurring");
        if (!recurringLink.isVisible()) {
            // If not visible, click to open the group
            page.locator("#nav-group-akuntansi").click();
            page.waitForTimeout(300);
        }

        assertThat(recurringLink).isVisible();
        recurringLink.click();
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).containsText("Transaksi Berulang");
    }

    @Test
    @DisplayName("Should create new recurring transaction via form")
    void shouldCreateRecurring() {
        navigateTo("/recurring/new");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).containsText("Transaksi Berulang Baru");

        page.fill("input[name='name']", "Sewa Kantor Bulanan");

        // Select template
        var templateSelect = page.locator("select[name='journalTemplateId']");
        var options = templateSelect.locator("option");
        if (options.count() > 1) {
            templateSelect.selectOption(new String[]{options.nth(1).getAttribute("value")});
        }

        page.fill("input[name='amount']", "5000000");
        page.fill("input[name='description']", "Pembayaran sewa kantor");
        page.selectOption("select[name='frequency']", "MONTHLY");
        page.fill("input[name='dayOfMonth']", "1");
        page.fill("input[name='startDate']", LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE));

        page.locator("#btn-simpan").click();
        waitForPageLoad();

        // Should redirect to list with success message
        assertThat(page.locator("body")).containsText("berhasil");
    }

    @Test
    @DisplayName("Should show detail page with preview occurrences")
    void shouldShowDetailWithPreview() {
        RecurringTransaction recurring = createTestRecurring("Detail Test", RecurringFrequency.MONTHLY);

        navigateTo("/recurring/" + recurring.getId());
        waitForPageLoad();

        takeScreenshot("recurring-detail-debug");

        assertThat(page.locator("#page-title, h1").first()).containsText("Detail Transaksi Berulang");
        assertThat(page.locator("[data-testid='recurring-detail']")).isVisible();
        assertThat(page.locator("body")).containsText("Detail Test");

        // Preview section should be visible for active recurring
        assertThat(page.locator("[data-testid='recurring-preview']")).isVisible();

        takeManualScreenshot("recurring/detail");
    }

    @Test
    @DisplayName("Should edit recurring transaction")
    void shouldEditRecurring() {
        RecurringTransaction recurring = createTestRecurring("Edit Test", RecurringFrequency.MONTHLY);

        navigateTo("/recurring/" + recurring.getId() + "/edit");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).containsText("Edit Transaksi Berulang");

        page.fill("input[name='name']", "Sewa Kantor Updated");
        page.fill("input[name='amount']", "6000000");

        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(page.locator("body")).containsText("berhasil");
    }

    @Test
    @DisplayName("Should pause recurring transaction")
    void shouldPauseRecurring() {
        RecurringTransaction recurring = createTestRecurring("Pause Test", RecurringFrequency.MONTHLY);

        navigateTo("/recurring/" + recurring.getId());
        waitForPageLoad();

        // Accept the confirm dialog
        page.onDialog(dialog -> dialog.accept());

        page.locator("#form-pause button[type='submit']").click();
        waitForPageLoad();

        assertThat(page.locator("body")).containsText("dijeda");

        // Verify status badge shows "Dijeda"
        assertThat(page.locator(".bg-yellow-100")).isVisible();
    }

    @Test
    @DisplayName("Should resume paused recurring transaction")
    void shouldResumeRecurring() {
        RecurringTransaction recurring = createTestRecurring("Resume Test", RecurringFrequency.MONTHLY);
        // Pause it first
        recurring.setStatus(RecurringStatus.PAUSED);
        recurringTransactionRepository.save(recurring);

        navigateTo("/recurring/" + recurring.getId());
        waitForPageLoad();

        page.locator("#form-resume button[type='submit']").click();
        waitForPageLoad();

        assertThat(page.locator("body")).containsText("dilanjutkan");

        // Verify status badge shows "Aktif"
        assertThat(page.locator(".bg-green-100")).isVisible();
    }

    @Test
    @DisplayName("Should complete recurring transaction")
    void shouldCompleteRecurring() {
        RecurringTransaction recurring = createTestRecurring("Complete Test", RecurringFrequency.MONTHLY);

        navigateTo("/recurring/" + recurring.getId());
        waitForPageLoad();

        page.onDialog(dialog -> dialog.accept());

        page.locator("#form-complete button[type='submit']").click();
        waitForPageLoad();

        assertThat(page.locator("body")).containsText("diselesaikan");

        // Verify status badge shows "Selesai"
        assertThat(page.locator(".bg-gray-100.text-gray-600")).isVisible();
    }

    @Test
    @DisplayName("Should delete recurring transaction")
    void shouldDeleteRecurring() {
        RecurringTransaction recurring = createTestRecurring("Delete Test", RecurringFrequency.MONTHLY);

        navigateTo("/recurring/" + recurring.getId());
        waitForPageLoad();

        page.onDialog(dialog -> dialog.accept());

        page.locator("#form-delete button[type='submit']").click();
        waitForPageLoad();

        assertThat(page.locator("body")).containsText("dihapus");
    }

    private RecurringTransaction createTestRecurring(String name, RecurringFrequency frequency) {
        List<JournalTemplate> templates = journalTemplateService.findAll();
        JournalTemplate template = templates.getFirst();

        RecurringTransaction recurring = new RecurringTransaction();
        recurring.setName(name);
        recurring.setJournalTemplate(template);
        recurring.setAmount(new BigDecimal("5000000"));
        recurring.setDescription("Test recurring: " + name);
        recurring.setFrequency(frequency);
        recurring.setDayOfMonth(1);
        recurring.setStartDate(LocalDate.now().plusDays(1));
        recurring.setNextRunDate(LocalDate.now().plusDays(1));
        recurring.setStatus(RecurringStatus.ACTIVE);
        recurring.setAutoPost(true);
        recurring.setCreatedBy("admin");

        return recurringTransactionRepository.save(recurring);
    }
}
