package com.artivisi.accountingfinance.functional.page;

import com.microsoft.playwright.Page;

import static org.assertj.core.api.Assertions.assertThat;

public class ProjectDetailPage {
    private final Page page;
    private final String baseUrl;

    private static final String PAGE_TITLE = "#page-title";
    private static final String PROJECT_DETAIL = "[data-testid='project-detail']";
    private static final String PROJECT_NAME = "[data-testid='project-detail'] h2";
    private static final String PROJECT_CODE = "[data-testid='project-detail'] p.font-mono";
    private static final String COMPLETE_BUTTON = "button:has-text('Selesaikan')";
    private static final String ARCHIVE_BUTTON = "button:has-text('Arsipkan')";
    private static final String REACTIVATE_BUTTON = "button:has-text('Aktifkan Kembali')";

    public ProjectDetailPage(Page page, String baseUrl) {
        this.page = page;
        this.baseUrl = baseUrl;
    }

    public ProjectDetailPage navigate(String projectId) {
        page.navigate(baseUrl + "/projects/" + projectId);
        return this;
    }

    public void assertPageTitleVisible() {
        assertThat(page.locator(PAGE_TITLE).isVisible()).isTrue();
    }

    public void assertProjectNameText(String expected) {
        assertThat(page.locator(PROJECT_NAME).textContent()).contains(expected);
    }

    public void assertProjectCodeText(String expected) {
        assertThat(page.locator(PROJECT_CODE).textContent()).contains(expected);
    }

    public void assertStatusText(String expected) {
        assertThat(page.locator(PROJECT_DETAIL).textContent()).contains(expected);
    }

    public void clickCompleteButton() {
        page.onceDialog(dialog -> dialog.accept());
        page.click(COMPLETE_BUTTON);
        page.waitForLoadState();
    }

    public void clickArchiveButton() {
        page.onceDialog(dialog -> dialog.accept());
        page.click(ARCHIVE_BUTTON);
        page.waitForLoadState();
    }

    public void clickReactivateButton() {
        page.click(REACTIVATE_BUTTON);
        page.waitForLoadState();
    }

    public boolean hasCompleteButton() {
        return page.locator(COMPLETE_BUTTON).count() > 0;
    }

    public boolean hasArchiveButton() {
        return page.locator(ARCHIVE_BUTTON).count() > 0;
    }

    public boolean hasReactivateButton() {
        return page.locator(REACTIVATE_BUTTON).count() > 0;
    }

    // Milestone methods
    private static final String MILESTONE_SECTION = "[data-testid='milestone-section']";
    private static final String NEW_MILESTONE_BUTTON = "#btn-new-milestone";
    private static final String MILESTONE_ITEM = "[data-milestone-id]";

    public void clickNewMilestoneButton() {
        page.click(NEW_MILESTONE_BUTTON);
        page.waitForLoadState();
    }

    public boolean hasMilestoneSection() {
        return page.locator(MILESTONE_SECTION).count() > 0;
    }

    public boolean hasNewMilestoneButton() {
        return page.locator(NEW_MILESTONE_BUTTON).count() > 0;
    }

    public int getMilestoneCount() {
        return page.locator(MILESTONE_ITEM).count();
    }

    public boolean hasMilestoneWithName(String name) {
        page.waitForLoadState();
        return page.getByText(name).count() > 0;
    }

    public void clickMilestoneStartButton(String milestoneName) {
        page.waitForLoadState();
        page.locator(MILESTONE_ITEM + ":has-text('" + milestoneName + "')").locator("button[title='Mulai']").click();
        page.waitForLoadState();
    }

    public void clickMilestoneCompleteButton(String milestoneName) {
        page.waitForLoadState();
        page.locator(MILESTONE_ITEM + ":has-text('" + milestoneName + "')").locator("button[title='Selesai']").click();
        page.waitForLoadState();
    }

    public void clickMilestoneResetButton(String milestoneName) {
        page.waitForLoadState();
        page.locator(MILESTONE_ITEM + ":has-text('" + milestoneName + "')").locator("button[title='Reset']").click();
        page.waitForLoadState();
    }

    public void clickMilestoneEditButton(String milestoneName) {
        page.waitForLoadState();
        page.locator(MILESTONE_ITEM + ":has-text('" + milestoneName + "')").locator("a[title='Edit']").click();
        page.waitForLoadState();
    }

    public void clickMilestoneDeleteButton(String milestoneName) {
        page.waitForLoadState();
        page.onceDialog(dialog -> dialog.accept());
        page.locator(MILESTONE_ITEM + ":has-text('" + milestoneName + "')").locator("button[title='Hapus']").click();
        page.waitForLoadState();
    }

    public String getMilestoneStatus(String milestoneName) {
        page.waitForLoadState();
        return page.locator(MILESTONE_ITEM + ":has-text('" + milestoneName + "')").locator("span.rounded-full").last().textContent().trim();
    }
}
