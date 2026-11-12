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

import java.math.BigDecimal;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@DisplayName("Alert Config Tests")
@Import(ServiceTestDataInitializer.class)
class AlertConfigTest extends PlaywrightTestBase {

    @Autowired
    private AlertRuleRepository alertRuleRepository;

    @Autowired
    private AlertEventRepository alertEventRepository;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Should display alert config page with 7 rules")
    void shouldDisplayAlertConfigPage() {
        navigateTo("/alerts/config");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).containsText("Konfigurasi Peringatan");

        // Verify all 7 alert types are listed (use exact text match on heading elements)
        assertThat(page.locator(".font-medium:text-is('Kas Rendah')")).isVisible();
        assertThat(page.locator(".font-medium:text-is('Piutang Jatuh Tempo')")).isVisible();
        assertThat(page.locator(".font-medium:text-is('Lonjakan Biaya')")).isVisible();
        assertThat(page.locator(".font-medium:text-is('Proyek Melebihi Anggaran')")).isVisible();
        assertThat(page.locator(".font-medium:text-is('Margin Proyek Turun')")).isVisible();
        assertThat(page.locator(".font-medium:text-is('Penagihan Melambat')")).isVisible();
        assertThat(page.locator(".font-medium:text-is('Konsentrasi Klien')")).isVisible();

        takeManualScreenshot("alerts/config");
    }

    @Test
    @DisplayName("Should navigate to alerts from sidebar")
    void shouldNavigateFromSidebar() {
        navigateTo("/");
        waitForPageLoad();

        // Open Laporan collapsible section
        page.locator("#nav-group-laporan").click();
        page.waitForTimeout(300);

        var sidebarLink = page.locator("#nav-alerts");
        assertThat(sidebarLink).isVisible();
        sidebarLink.click();
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).containsText("Peringatan Aktif");
    }

    @Test
    @DisplayName("Should update alert threshold")
    void shouldUpdateAlertThreshold() {
        // Rules are ordered by alert type, so target CASH_LOW by id: editing "the first rule" changed
        // BANK_STATEMENT_MISSING's grace days and broke the bank statement archive grid for later tests.
        AlertRule rule = alertRuleRepository.findByAlertType(AlertType.CASH_LOW).orElseThrow();
        BigDecimal originalThreshold = rule.getThreshold();
        try {
            navigateTo("/alerts/config");
            waitForPageLoad();

            page.locator("#threshold-" + rule.getId()).fill("20000000");
            ruleForm(rule).locator("button:has-text('Simpan')").click();
            waitForPageLoad();

            assertThat(page.locator("body")).containsText("berhasil diperbarui");
            assertThat(page.locator("#threshold-" + rule.getId())).hasValue("20000000.00");
            org.assertj.core.api.Assertions.assertThat(
                    alertRuleRepository.findByAlertType(AlertType.CASH_LOW).orElseThrow().getThreshold())
                    .isEqualByComparingTo("20000000");
        } finally {
            AlertRule stored = alertRuleRepository.findByAlertType(AlertType.CASH_LOW).orElseThrow();
            stored.setThreshold(originalThreshold);
            alertRuleRepository.saveAndFlush(stored);
        }
    }

    @Test
    @DisplayName("Should toggle alert disabled")
    void shouldToggleAlertDisabled() {
        AlertRule rule = alertRuleRepository.findByAlertType(AlertType.CASH_LOW).orElseThrow();
        boolean originalEnabled = rule.isEnabled();
        try {
            if (!originalEnabled) {
                rule.setEnabled(true);
                alertRuleRepository.saveAndFlush(rule);
            }
            navigateTo("/alerts/config");
            waitForPageLoad();

            page.locator("#enabled-" + rule.getId()).uncheck();
            ruleForm(rule).locator("button:has-text('Simpan')").click();
            waitForPageLoad();

            assertThat(page.locator("body")).containsText("berhasil diperbarui");
            assertThat(page.locator("#enabled-" + rule.getId())).not().isChecked();
            org.assertj.core.api.Assertions.assertThat(
                    alertRuleRepository.findByAlertType(AlertType.CASH_LOW).orElseThrow().isEnabled()).isFalse();
        } finally {
            AlertRule stored = alertRuleRepository.findByAlertType(AlertType.CASH_LOW).orElseThrow();
            stored.setEnabled(originalEnabled);
            alertRuleRepository.saveAndFlush(stored);
        }
    }

    private com.microsoft.playwright.Locator ruleForm(AlertRule rule) {
        return page.locator("form[action$='/alerts/config/" + rule.getId() + "']");
    }

    @Test
    @DisplayName("Should display alert history page")
    void shouldDisplayAlertHistory() {
        navigateTo("/alerts/history");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).containsText("Riwayat Peringatan");

        takeManualScreenshot("alerts/history");
    }

    @Test
    @DisplayName("Should filter alert history by alert type")
    void shouldFilterAlertHistoryByType() {
        // Create a test event for filtering
        AlertRule cashLowRule = alertRuleRepository.findByAlertType(AlertType.CASH_LOW)
                .orElseThrow();
        AlertEvent event = new AlertEvent();
        event.setAlertRule(cashLowRule);
        event.setSeverity(AlertSeverity.WARNING);
        event.setMessage("Filter test: kas rendah");
        alertEventRepository.save(event);

        navigateTo("/alerts/history?alertType=CASH_LOW");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).containsText("Riwayat Peringatan");
    }

    @Test
    @DisplayName("Should filter alert history by severity")
    void shouldFilterAlertHistoryBySeverity() {
        navigateTo("/alerts/history?severity=WARNING");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).containsText("Riwayat Peringatan");
    }

    @Test
    @DisplayName("Should filter alert history by acknowledged status")
    void shouldFilterAlertHistoryByAcknowledged() {
        navigateTo("/alerts/history?acknowledged=false");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).containsText("Riwayat Peringatan");
    }

    @Test
    @DisplayName("Should filter alert history with combined filters")
    void shouldFilterAlertHistoryWithCombinedFilters() {
        navigateTo("/alerts/history?alertType=CASH_LOW&severity=WARNING&acknowledged=false");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).containsText("Riwayat Peringatan");
    }

    @Test
    @DisplayName("Should paginate alert history")
    void shouldPaginateAlertHistory() {
        navigateTo("/alerts/history?page=0&size=5");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).containsText("Riwayat Peringatan");
    }

    @Test
    @DisplayName("Should display active alerts page")
    void shouldDisplayActiveAlertsPage() {
        navigateTo("/alerts");
        waitForPageLoad();

        assertThat(page.locator("#page-title, h1").first()).containsText("Peringatan Aktif");
    }
}
