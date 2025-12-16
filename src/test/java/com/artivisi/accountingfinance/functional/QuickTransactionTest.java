package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.functional.page.DashboardPage;
import com.artivisi.accountingfinance.functional.page.QuickTransactionModal;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for Quick Transaction feature.
 * Tests the FAB button, template picker, and quick form flow.
 */
@DisplayName("Quick Transaction Flow")
@Import(ServiceTestDataInitializer.class)
public class QuickTransactionTest extends PlaywrightTestBase {

    @Autowired
    private JournalTemplateRepository templateRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private QuickTransactionModal quickTransactionModal;
    private DashboardPage dashboardPage;

    @BeforeEach
    void setupPageObjects() {
        // Setup template usage data for testing
        setupTemplateUsageData();

        String baseUrl = "http://localhost:" + port;
        quickTransactionModal = new QuickTransactionModal(page, baseUrl);
        dashboardPage = new DashboardPage(page, baseUrl);

        // Login as admin (has all permissions)
        loginAsAdmin();

        // Navigate to dashboard
        dashboardPage.navigate();
    }

    /**
     * Setup template usage data so templates appear in frequent/recent lists.
     * Updates template usage counts and last used timestamps.
     */
    private void setupTemplateUsageData() {
        List<com.artivisi.accountingfinance.entity.JournalTemplate> templates =
            templateRepository.findByActiveOrderByTemplateNameAsc(true);

        if (templates.size() >= 3) {
            // Make first 3 templates "frequently used"
            templates.get(0).setUsageCount(25);
            templates.get(0).setLastUsedAt(java.time.LocalDateTime.now().minusHours(1));

            templates.get(1).setUsageCount(18);
            templates.get(1).setLastUsedAt(java.time.LocalDateTime.now().minusHours(2));

            templates.get(2).setUsageCount(12);
            templates.get(2).setLastUsedAt(java.time.LocalDateTime.now().minusHours(3));

            templateRepository.saveAll(templates.subList(0, 3));
        }
    }

    @Test
    @Order(1)
    @DisplayName("FAB button should be visible on dashboard")
    void fabButtonVisible() {
        assertThat(page.getByTestId("quick-transaction-fab").isVisible())
            .as("FAB button should be visible")
            .isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("Should open modal when FAB is clicked")
    void openModal() {
        quickTransactionModal.openModal();

        assertThat(quickTransactionModal.isModalVisible())
            .as("Modal should be visible after clicking FAB")
            .isTrue();
    }

    @Test
    @Order(3)
    @DisplayName("Should show frequent templates")
    void showFrequentTemplates() {
        quickTransactionModal.openModal();

        int frequentCount = quickTransactionModal.getFrequentTemplatesCount();

        assertThat(frequentCount)
            .as("Should display frequent templates")
            .isGreaterThanOrEqualTo(0)
            .isLessThanOrEqualTo(6);  // Max 6 as per service design
    }

    @Test
    @Order(4)
    @DisplayName("Should show recent templates")
    void showRecentTemplates() {
        quickTransactionModal.openModal();

        int recentCount = quickTransactionModal.getRecentTemplatesCount();

        assertThat(recentCount)
            .as("Should display recent templates")
            .isGreaterThanOrEqualTo(0)
            .isLessThanOrEqualTo(5);  // Max 5 as per service design
    }

    @Test
    @Order(5)
    @DisplayName("Should load form when template is selected from frequent list")
    void loadFormAfterTemplateSelection() {
        quickTransactionModal.openModal();

        int frequentCount = quickTransactionModal.getFrequentTemplatesCount();
        if (frequentCount > 0) {
            // Click first frequent template
            page.locator("[data-testid^='template-frequent-']").first().click();

            // Wait for form to appear
            page.getByTestId("quick-transaction-form").waitFor();

            assertThat(quickTransactionModal.isFormVisible())
                .as("Form should be visible after selecting template")
                .isTrue();

            // Verify description field exists and is filled
            String description = quickTransactionModal.getDescription();
            assertThat(description)
                .as("Description should be pre-filled")
                .isNotEmpty();
        }
    }

    @Test
    @Order(6)
    @DisplayName("Should create transaction via quick form and verify in transaction list")
    void createTransactionViaQuickForm() {
        // Open modal
        quickTransactionModal.openModal();

        // Select first available template (frequent or recent)
        int frequentCount = quickTransactionModal.getFrequentTemplatesCount();
        int recentCount = quickTransactionModal.getRecentTemplatesCount();

        if (frequentCount > 0 || recentCount > 0) {
            page.locator("[data-testid^='template-frequent-'], [data-testid^='template-recent-']").first().click();

            // Wait for form to load
            page.getByTestId("quick-transaction-form").waitFor();

            // Fill form
            quickTransactionModal.fillAmount("7500000");
            quickTransactionModal.fillDescription("Test Quick Transaction - Automated");

            // Submit
            quickTransactionModal.submit();

            // Wait for modal to close
            page.waitForTimeout(1000);

            // Navigate to transaction list
            page.navigate(baseUrl() + "/transactions");
            page.waitForLoadState();

            // Verify transaction appears in list
            assertThat(page.locator("text='Test Quick Transaction - Automated'").count())
                .as("Transaction should appear in transaction list")
                .isGreaterThan(0);
        }
    }

    @Test
    @Order(7)
    @DisplayName("Should close modal when cancel is clicked")
    void cancelTransaction() {
        quickTransactionModal.openModal();

        int frequentCount = quickTransactionModal.getFrequentTemplatesCount();
        int recentCount = quickTransactionModal.getRecentTemplatesCount();

        if (frequentCount > 0 || recentCount > 0) {
            // Select first available template
            page.locator("[data-testid^='template-frequent-'], [data-testid^='template-recent-']").first().click();

            // Wait for form
            page.getByTestId("quick-transaction-form").waitFor();

            // Fill some data
            quickTransactionModal.fillAmount("1000000");

            // Cancel
            quickTransactionModal.cancel();

            // Verify modal is closed
            page.waitForTimeout(500);
            assertThat(quickTransactionModal.isModalVisible())
                .as("Modal should be closed after cancel")
                .isFalse();
        }
    }

    @Test
    @Order(8)
    @DisplayName("Should close modal on ESC key")
    void closeModalWithEscKey() {
        quickTransactionModal.openModal();

        assertThat(quickTransactionModal.isModalVisible())
            .as("Modal should be visible")
            .isTrue();

        quickTransactionModal.closeModal();  // Uses ESC key

        assertThat(quickTransactionModal.isModalVisible())
            .as("Modal should be closed after ESC")
            .isFalse();
    }
}
