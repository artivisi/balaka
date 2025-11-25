package com.artivisi.accountingfinance.functional.page;

import com.microsoft.playwright.Page;

import static org.assertj.core.api.Assertions.assertThat;

public class MilestoneFormPage {
    private final Page page;
    private final String baseUrl;

    private static final String PAGE_TITLE = "#page-title";
    private static final String SEQUENCE_INPUT = "#sequence";
    private static final String NAME_INPUT = "#name";
    private static final String WEIGHT_INPUT = "#weightPercent";
    private static final String TARGET_DATE_INPUT = "#targetDate";
    private static final String DESCRIPTION_INPUT = "#description";
    private static final String SUBMIT_BUTTON = "#btn-simpan";

    public MilestoneFormPage(Page page, String baseUrl) {
        this.page = page;
        this.baseUrl = baseUrl;
    }

    public MilestoneFormPage navigateToNew(String projectId) {
        page.navigate(baseUrl + "/projects/" + projectId + "/milestones/new");
        return this;
    }

    public void assertPageTitleText(String expected) {
        assertThat(page.locator(PAGE_TITLE).textContent()).contains(expected);
    }

    public void fillSequence(String sequence) {
        page.fill(SEQUENCE_INPUT, sequence);
    }

    public void fillName(String name) {
        page.fill(NAME_INPUT, name);
    }

    public void fillWeight(String weight) {
        page.fill(WEIGHT_INPUT, weight);
    }

    public void fillTargetDate(String date) {
        page.fill(TARGET_DATE_INPUT, date);
    }

    public void fillDescription(String description) {
        page.fill(DESCRIPTION_INPUT, description);
    }

    public void clickSubmit() {
        page.click(SUBMIT_BUTTON);
        page.waitForLoadState();
    }

    public String getNameValue() {
        return page.inputValue(NAME_INPUT);
    }

    public String getWeightValue() {
        return page.inputValue(WEIGHT_INPUT);
    }
}
