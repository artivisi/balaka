package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.AlertEvent;
import com.artivisi.accountingfinance.entity.AlertRule;
import com.artivisi.accountingfinance.enums.AlertSeverity;
import com.artivisi.accountingfinance.enums.AlertType;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.AlertEventRepository;
import com.artivisi.accountingfinance.repository.AlertRuleRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@DisplayName("Alert Dashboard Widget Tests")
@Import(ServiceTestDataInitializer.class)
class AlertDashboardWidgetTest extends PlaywrightTestBase {

    @Autowired
    private AlertRuleRepository alertRuleRepository;

    @Autowired
    private AlertEventRepository alertEventRepository;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Should display alert widget on dashboard")
    void shouldDisplayAlertWidgetOnDashboard() {
        navigateTo("/dashboard");
        waitForPageLoad();

        // Wait for HTMX widget to load
        page.waitForSelector("#alert-widget-content", new com.microsoft.playwright.Page.WaitForSelectorOptions().setTimeout(10000));
        assertThat(page.locator("#alert-widget-content")).isVisible();
        assertThat(page.locator("#alert-widget-content")).containsText("Peringatan");
    }

    @Test
    @DisplayName("Should acknowledge alert from active page")
    void shouldAcknowledgeAlertFromActivePage() {
        // Create a test alert event programmatically
        AlertRule cashLowRule = alertRuleRepository.findByAlertType(AlertType.CASH_LOW)
                .orElseThrow();

        AlertEvent event = new AlertEvent();
        event.setAlertRule(cashLowRule);
        event.setSeverity(AlertSeverity.WARNING);
        event.setMessage("Test: Saldo kas rendah");
        alertEventRepository.save(event);

        // Navigate to active alerts
        navigateTo("/alerts");
        waitForPageLoad();

        // Verify alert is visible
        assertThat(page.locator("text=Test: Saldo kas rendah")).isVisible();

        // Acknowledge it
        page.locator("button:has-text('Konfirmasi')").first().click();
        waitForPageLoad();

        // Verify success
        assertThat(page.locator("body")).containsText("telah dikonfirmasi");
    }
}
