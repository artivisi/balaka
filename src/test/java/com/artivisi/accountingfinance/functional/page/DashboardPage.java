package com.artivisi.accountingfinance.functional.page;

import com.microsoft.playwright.Page;
import static org.assertj.core.api.Assertions.assertThat;

public class DashboardPage {
    private final Page page;
    private final String baseUrl;

    private static final String PAGE_TITLE = "#page-title";
    private static final String KPI_CONTAINER = "#dashboard-kpis-container";
    private static final String KPI_PERIOD = "#kpi-period";
    private static final String MONTH_SELECTOR = "#month-selector";
    private static final String CARD_PENDAPATAN = "#card-pendapatan";
    private static final String CARD_PENGELUARAN = "#card-pengeluaran";
    private static final String CARD_LABA_BERSIH = "#card-laba-bersih";

    public DashboardPage(Page page, String baseUrl) {
        this.page = page;
        this.baseUrl = baseUrl;
    }

    public DashboardPage navigate() {
        page.navigate(baseUrl + "/dashboard");
        page.waitForLoadState();
        return this;
    }

    public void waitForKPIsToLoad() {
        // Wait for HTMX to load the KPIs
        page.waitForSelector(CARD_PENDAPATAN, new Page.WaitForSelectorOptions().setTimeout(10000));
    }

    public void assertPageTitleVisible() {
        assertThat(page.locator(PAGE_TITLE).isVisible()).isTrue();
    }

    public void assertPageTitleText(String expected) {
        assertThat(page.locator(PAGE_TITLE).textContent().trim()).isEqualTo(expected);
    }

    public void assertKPIContainerVisible() {
        assertThat(page.locator(KPI_CONTAINER).isVisible()).isTrue();
    }

    public void assertKPIPeriodVisible() {
        assertThat(page.locator(KPI_PERIOD).isVisible()).isTrue();
    }

    public String getKPIPeriodText() {
        return page.locator(KPI_PERIOD).textContent().trim();
    }

    public void assertMonthSelectorVisible() {
        assertThat(page.locator(MONTH_SELECTOR).isVisible()).isTrue();
    }

    public void selectMonth(String yearMonth) {
        // Clear and fill the month input
        page.locator(MONTH_SELECTOR).fill(yearMonth);
        // Trigger change event and blur to ensure HTMX picks it up
        page.locator(MONTH_SELECTOR).dispatchEvent("change");
        // Wait for network request to complete
        page.waitForResponse(response -> response.url().contains("/dashboard/kpis"),
            () -> page.locator(MONTH_SELECTOR).blur());
        page.waitForLoadState();
        // Wait for HTMX to update the card
        page.waitForSelector(CARD_PENDAPATAN, new Page.WaitForSelectorOptions().setTimeout(10000));
    }

    public void assertRevenueCardVisible() {
        assertThat(page.locator(CARD_PENDAPATAN).isVisible()).isTrue();
    }

    public void assertExpenseCardVisible() {
        assertThat(page.locator(CARD_PENGELUARAN).isVisible()).isTrue();
    }

    public void assertProfitCardVisible() {
        assertThat(page.locator(CARD_LABA_BERSIH).isVisible()).isTrue();
    }

    public String getRevenueCardText() {
        return page.locator(CARD_PENDAPATAN).textContent();
    }

    public String getExpenseCardText() {
        return page.locator(CARD_PENGELUARAN).textContent();
    }

    public String getProfitCardText() {
        return page.locator(CARD_LABA_BERSIH).textContent();
    }

    public boolean hasRevenueValue() {
        String text = getRevenueCardText();
        return text.contains("Rp") && text.contains("jt");
    }

    public boolean hasExpenseValue() {
        String text = getExpenseCardText();
        return text.contains("Rp") && text.contains("jt");
    }

    public boolean hasProfitValue() {
        String text = getProfitCardText();
        return text.contains("Rp") && text.contains("jt");
    }

    public boolean hasPercentageChange() {
        String text = getRevenueCardText();
        return text.contains("%");
    }

    // Amortization widget methods
    private static final String AMORTIZATION_WIDGET = "#amortization-widget";
    private static final String AMORTIZATION_WIDGET_CONTENT = "#amortization-widget-content";

    public void waitForAmortizationWidgetToLoad() {
        page.waitForSelector(AMORTIZATION_WIDGET_CONTENT, new Page.WaitForSelectorOptions().setTimeout(10000));
    }

    public void assertAmortizationWidgetVisible() {
        assertThat(page.locator(AMORTIZATION_WIDGET).isVisible()).isTrue();
    }

    public void assertAmortizationWidgetContentVisible() {
        assertThat(page.locator(AMORTIZATION_WIDGET_CONTENT).isVisible()).isTrue();
    }

    public String getAmortizationWidgetText() {
        return page.locator(AMORTIZATION_WIDGET_CONTENT).textContent();
    }

    public boolean hasAmortizationTitle() {
        return getAmortizationWidgetText().contains("Amortisasi");
    }

    public boolean hasAmortizationLink() {
        return page.locator(AMORTIZATION_WIDGET_CONTENT + " a[href='/amortization']").count() > 0;
    }
}
