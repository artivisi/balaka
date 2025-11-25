package com.artivisi.accountingfinance.functional.page;

import com.microsoft.playwright.Page;

import static org.assertj.core.api.Assertions.assertThat;

public class AmortizationFormPage {
    private final Page page;
    private final String baseUrl;

    // Locators
    private static final String PAGE_TITLE = "#page-title";
    private static final String CODE_INPUT = "[data-testid='input-code']";
    private static final String NAME_INPUT = "[data-testid='input-name']";
    private static final String TYPE_SELECT = "[data-testid='select-type']";
    private static final String SOURCE_ACCOUNT_SELECT = "[data-testid='select-source-account']";
    private static final String TARGET_ACCOUNT_SELECT = "[data-testid='select-target-account']";
    private static final String AMOUNT_INPUT = "[data-testid='input-amount']";
    private static final String START_DATE_INPUT = "[data-testid='input-start-date']";
    private static final String END_DATE_INPUT = "[data-testid='input-end-date']";
    private static final String FREQUENCY_SELECT = "[data-testid='select-frequency']";
    private static final String AUTO_POST_CHECKBOX = "[data-testid='checkbox-autopost']";
    private static final String SUBMIT_BUTTON = "[data-testid='btn-submit']";

    public AmortizationFormPage(Page page, String baseUrl) {
        this.page = page;
        this.baseUrl = baseUrl;
    }

    public AmortizationFormPage navigateToNew() {
        page.navigate(baseUrl + "/amortization/new");
        return this;
    }

    public AmortizationFormPage navigateToEdit(String scheduleId) {
        page.navigate(baseUrl + "/amortization/" + scheduleId + "/edit");
        return this;
    }

    public void assertPageTitleText(String expected) {
        assertThat(page.locator(PAGE_TITLE).textContent()).contains(expected);
    }

    public void fillCode(String code) {
        page.fill(CODE_INPUT, code);
    }

    public void fillName(String name) {
        page.fill(NAME_INPUT, name);
    }

    public void selectType(String type) {
        page.selectOption(TYPE_SELECT, type);
    }

    public void selectSourceAccount(String accountId) {
        page.selectOption(SOURCE_ACCOUNT_SELECT, accountId);
    }

    public void selectTargetAccount(String accountId) {
        page.selectOption(TARGET_ACCOUNT_SELECT, accountId);
    }

    public void fillAmount(String amount) {
        page.fill(AMOUNT_INPUT, amount);
    }

    public void fillStartDate(String date) {
        page.fill(START_DATE_INPUT, date);
    }

    public void fillEndDate(String date) {
        page.fill(END_DATE_INPUT, date);
    }

    public void selectFrequency(String frequency) {
        page.selectOption(FREQUENCY_SELECT, frequency);
    }

    public void setAutoPost(boolean autoPost) {
        if (autoPost) {
            page.check(AUTO_POST_CHECKBOX);
        } else {
            page.uncheck(AUTO_POST_CHECKBOX);
        }
    }

    public void clickSubmit() {
        page.click(SUBMIT_BUTTON);
        page.waitForLoadState();
    }

    public String getFirstSourceAccountId() {
        return page.locator(SOURCE_ACCOUNT_SELECT + " option").nth(1).getAttribute("value");
    }

    public String getFirstTargetAccountId() {
        return page.locator(TARGET_ACCOUNT_SELECT + " option").nth(2).getAttribute("value");
    }
}
