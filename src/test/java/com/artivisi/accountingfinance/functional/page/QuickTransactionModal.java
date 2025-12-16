package com.artivisi.accountingfinance.functional.page;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Page Object for Quick Transaction Modal.
 * Provides methods to interact with the FAB and quick transaction flow.
 */
public class QuickTransactionModal {
    private final Page page;
    private final String baseUrl;

    public QuickTransactionModal(Page page, String baseUrl) {
        this.page = page;
        this.baseUrl = baseUrl;
    }

    /**
     * Open the quick transaction modal by clicking the FAB.
     */
    public void openModal() {
        page.getByTestId("quick-transaction-fab").click();
        // Wait for modal to be visible
        page.getByTestId("quick-transaction-modal").waitFor();
    }

    /**
     * Check if the modal is visible.
     */
    public boolean isModalVisible() {
        return page.getByTestId("quick-transaction-modal").isVisible();
    }

    /**
     * Get the count of frequent templates displayed.
     */
    public int getFrequentTemplatesCount() {
        return page.locator("[data-testid^='template-frequent-']").count();
    }

    /**
     * Get the count of recent templates displayed.
     */
    public int getRecentTemplatesCount() {
        return page.locator("[data-testid^='template-recent-']").count();
    }

    /**
     * Select a template by ID (from frequent or recent list).
     */
    public void selectTemplate(String templateId) {
        Locator templateButton = page.locator(
            "[data-testid='template-frequent-" + templateId + "'], " +
            "[data-testid='template-recent-" + templateId + "']"
        ).first();
        templateButton.click();
        // Wait for form to load
        page.getByTestId("quick-transaction-form").waitFor();
    }

    /**
     * Check if the quick transaction form is visible.
     */
    public boolean isFormVisible() {
        return page.getByTestId("quick-transaction-form").isVisible();
    }

    /**
     * Fill the amount field.
     */
    public void fillAmount(String amount) {
        page.getByTestId("quick-amount").fill(amount);
    }

    /**
     * Fill the description field.
     */
    public void fillDescription(String description) {
        page.getByTestId("quick-description").fill(description);
    }

    /**
     * Get the current value of the description field.
     */
    public String getDescription() {
        return page.getByTestId("quick-description").inputValue();
    }

    /**
     * Submit the quick transaction form.
     */
    public void submit() {
        page.getByTestId("quick-submit-button").click();
    }

    /**
     * Cancel the quick transaction.
     */
    public void cancel() {
        page.getByTestId("quick-cancel-button").click();
    }

    /**
     * Close the modal.
     */
    public void closeModal() {
        // Click outside the modal or use the close button
        page.keyboard().press("Escape");
    }

    /**
     * Wait for modal to close.
     */
    public void waitForModalToClose() {
        page.getByTestId("quick-transaction-modal").waitFor(
            new Locator.WaitForOptions().setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN)
        );
    }
}
