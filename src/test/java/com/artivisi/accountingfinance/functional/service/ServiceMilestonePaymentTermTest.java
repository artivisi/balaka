package com.artivisi.accountingfinance.functional.service;

import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.entity.Project;
import com.artivisi.accountingfinance.entity.ProjectMilestone;
import com.artivisi.accountingfinance.entity.ProjectPaymentTerm;
import com.artivisi.accountingfinance.enums.PaymentTrigger;
import com.artivisi.accountingfinance.repository.ClientRepository;
import com.artivisi.accountingfinance.repository.InvoiceRepository;
import com.artivisi.accountingfinance.repository.ProjectPaymentTermRepository;
import com.artivisi.accountingfinance.service.ProjectMilestoneService;
import com.artivisi.accountingfinance.service.ProjectPaymentTermService;
import com.artivisi.accountingfinance.service.ProjectService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Milestones and payment terms working together on the project detail page: empty states,
 * client requirement for terms, sequence ordering, weighted progress, overdue badges,
 * milestone-linked terms and the invoice due date derived from milestone completion,
 * and read-only sections on a completed project.
 *
 * Single-controller CRUD and validation live in MilestoneControllerFunctionalTest and
 * PaymentTermControllerFunctionalTest. Each test creates its own project (unique code).
 */
@DisplayName("Service Industry - Milestones & Payment Terms")
@Import(ServiceTestDataInitializer.class)
class ServiceMilestonePaymentTermTest extends PlaywrightTestBase {

    // From src/test/resources/testdata/service/clients.csv
    private static final String CLIENT_CODE = "TELKOM";

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ProjectMilestoneService milestoneService;

    @Autowired
    private ProjectPaymentTermService paymentTermService;

    @Autowired
    private ProjectPaymentTermRepository paymentTermRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @BeforeEach
    void setup() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Should show empty milestone and payment term sections with add buttons for new project")
    void shouldShowEmptySectionsForNewProject() {
        Project project = createProject(true);

        openProjectDetail(project);

        assertThat(page.locator("[data-testid='project-progress']")).hasText("0%");
        assertThat(milestoneSection()).containsText("Belum ada milestone");
        assertThat(milestoneSection().locator("[data-milestone-id]")).hasCount(0);
        assertThat(page.locator("#btn-new-milestone"))
                .hasAttribute("href", "/projects/" + project.getCode() + "/milestones/new");
        assertThat(termSection()).containsText("Belum ada termin pembayaran");
        assertThat(termSection().locator("[data-payment-term-id]")).hasCount(0);
        assertThat(page.locator("#btn-new-payment-term"))
                .hasAttribute("href", "/projects/" + project.getCode() + "/payment-terms/new");
    }

    @Test
    @DisplayName("Should require a client before payment terms can be added")
    void shouldRequireClientForPaymentTerms() {
        Project project = createProject(false);

        openProjectDetail(project);

        assertThat(termSection()).containsText("Pilih klien terlebih dahulu untuk membuat termin pembayaran");
        assertThat(termSection()).not().containsText("Belum ada termin pembayaran");
        assertThat(page.locator("#btn-new-payment-term")).hasCount(0);
        assertThat(page.locator("#btn-new-milestone")).hasCount(1);
    }

    @Test
    @DisplayName("Should list milestones by sequence and compute weighted progress as milestones change status")
    void shouldTrackWeightedProgress() {
        Project project = createProject(true);
        ProjectMilestone second = createMilestone(project, 2, "Implementasi " + shortId(), 70, null);
        ProjectMilestone first = createMilestone(project, 1, "Desain " + shortId(), 30, null);

        openProjectDetail(project);
        Locator rows = milestoneSection().locator("[data-milestone-id]");
        assertThat(rows).hasCount(2);
        assertEquals(first.getId().toString(), rows.nth(0).getAttribute("data-milestone-id"));
        assertEquals(second.getId().toString(), rows.nth(1).getAttribute("data-milestone-id"));
        assertThat(page.locator("[data-testid='project-progress']")).hasText("0%");

        clickMilestoneAction(first, "start");
        assertThat(page.locator("[data-testid='project-progress']")).hasText("0%");

        clickMilestoneAction(first, "complete");
        assertThat(page.locator("[data-testid='project-progress']")).hasText("30%");

        clickMilestoneAction(second, "start");
        clickMilestoneAction(second, "complete");
        assertThat(page.locator("[data-testid='project-progress']")).hasText("100%");

        clickMilestoneAction(first, "reset");
        assertThat(page.locator("[data-testid='project-progress']")).hasText("70%");
    }

    @Test
    @DisplayName("Should flag unfinished milestone past its target date as overdue")
    void shouldFlagOverdueMilestone() {
        Project project = createProject(true);
        LocalDate yesterday = LocalDate.now().minusDays(1);
        ProjectMilestone pending = createMilestone(project, 1, "Terlambat Pending " + shortId(), 10, yesterday);
        ProjectMilestone inProgress = createMilestone(project, 2, "Terlambat Proses " + shortId(), 10, yesterday);
        ProjectMilestone completed = createMilestone(project, 3, "Selesai " + shortId(), 10, yesterday);
        ProjectMilestone future = createMilestone(project, 4, "Masa Depan " + shortId(), 10,
                LocalDate.now().plusDays(30));
        milestoneService.startMilestone(inProgress.getId());
        milestoneService.startMilestone(completed.getId());
        milestoneService.completeMilestone(completed.getId());

        openProjectDetail(project);

        assertBadges(pending, "Terlambat");
        assertBadges(inProgress, "Terlambat");
        assertBadges(completed, "Selesai");
        assertBadges(future, "Pending");
    }

    @Test
    @DisplayName("Should link a new payment term to a project milestone")
    void shouldLinkPaymentTermToMilestone() {
        Project project = createProject(true);
        ProjectMilestone milestone = createMilestone(project, 1, "Go Live " + shortId(), 100, null);
        String termName = "Termin Go Live " + shortId();

        navigateTo("/projects/" + project.getCode() + "/payment-terms/new");
        waitForPageLoad();
        page.locator("#name").fill(termName);
        page.locator("#dueTrigger").selectOption("ON_MILESTONE");
        page.locator("#milestone").selectOption(milestone.getId().toString());
        page.locator("#percentage").fill("25");
        page.locator("#btn-simpan").click();
        waitForPageLoad();

        assertThat(page.locator("[data-testid='flash-success']")).hasText("Termin pembayaran berhasil ditambahkan");
        List<ProjectPaymentTerm> linked = paymentTermRepository.findByMilestoneId(milestone.getId());
        assertEquals(1, linked.size());
        ProjectPaymentTerm term = linked.get(0);
        assertEquals(termName, term.getName());
        assertEquals(PaymentTrigger.ON_MILESTONE, term.getDueTrigger());

        Locator row = termSection().locator("[data-payment-term-id='" + term.getId() + "']");
        assertThat(row).containsText("25.00% dari kontrak");
        assertThat(row).containsText("Saat Milestone Selesai");
        assertThat(row).containsText("Milestone: " + milestone.getName());
    }

    @Test
    @DisplayName("Should invoice milestone-linked term for its share of contract, due 14 days after milestone completion")
    void shouldInvoiceMilestoneTermDueAfterCompletion() {
        Project project = createProject(true);
        ProjectMilestone milestone = createMilestone(project, 1, "UAT " + shortId(), 100, null);
        ProjectPaymentTerm term = paymentTermService.create(project.getId(),
                newTerm(1, "Termin UAT " + shortId(), PaymentTrigger.ON_MILESTONE, new BigDecimal("25"), milestone));

        openProjectDetail(project);
        clickMilestoneAction(milestone, "start");
        clickMilestoneAction(milestone, "complete");
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Milestone selesai");

        page.onceDialog(dialog -> dialog.accept());
        termSection().locator("[data-payment-term-id='" + term.getId() + "'] form[action$='/generate-invoice'] button")
                .click();
        waitForPageLoad();

        List<Invoice> invoices = invoiceRepository.findByPaymentTermId(term.getId());
        assertEquals(1, invoices.size());
        Invoice invoice = invoices.get(0);
        assertEquals(baseUrl() + "/invoices/" + invoice.getInvoiceNumber(), page.url());
        assertEquals(0, new BigDecimal("50000000").compareTo(invoice.getAmount()),
                "25% of contract 200,000,000 expected, got " + invoice.getAmount());
        assertEquals(LocalDate.now().plusDays(14), invoice.getDueDate());
    }

    @Test
    @DisplayName("Should show milestones and terms without actions on a completed project")
    void shouldHideActionsOnCompletedProject() {
        Project project = createProject(true);
        ProjectMilestone milestone = createMilestone(project, 1, "Final " + shortId(), 100, null);
        ProjectPaymentTerm term = paymentTermService.create(project.getId(),
                newTerm(1, "Pelunasan " + shortId(), PaymentTrigger.ON_COMPLETION, new BigDecimal("100"), null));
        projectService.complete(project.getId());

        openProjectDetail(project);

        assertThat(page.locator("[data-milestone-id='" + milestone.getId() + "']")).containsText(milestone.getName());
        assertThat(page.locator("[data-payment-term-id='" + term.getId() + "']")).containsText(term.getName());
        assertThat(page.locator("#btn-new-milestone")).hasCount(0);
        assertThat(page.locator("#btn-new-payment-term")).hasCount(0);
        assertThat(milestoneSection().locator("form, a[title='Edit']")).hasCount(0);
        assertThat(termSection().locator("form, a[title='Edit']")).hasCount(0);
    }

    // ==================== helpers ====================

    private Project createProject(boolean withClient) {
        Project p = new Project();
        p.setCode("SMP-" + shortId());
        p.setName("Proyek Integrasi " + shortId());
        p.setContractValue(new BigDecimal("200000000"));
        UUID clientId = withClient
                ? clientRepository.findByCode(CLIENT_CODE)
                        .orElseThrow(() -> new IllegalStateException("Seeded client missing: " + CLIENT_CODE))
                        .getId()
                : null;
        return projectService.create(p, clientId);
    }

    private ProjectMilestone createMilestone(Project project, int sequence, String name, int weight,
                                             LocalDate targetDate) {
        ProjectMilestone m = new ProjectMilestone();
        m.setSequence(sequence);
        m.setName(name);
        m.setWeightPercent(weight);
        m.setTargetDate(targetDate);
        return milestoneService.create(project.getId(), m);
    }

    private static ProjectPaymentTerm newTerm(int sequence, String name, PaymentTrigger trigger,
                                              BigDecimal percentage, ProjectMilestone milestone) {
        ProjectPaymentTerm t = new ProjectPaymentTerm();
        t.setSequence(sequence);
        t.setName(name);
        t.setDueTrigger(trigger);
        t.setPercentage(percentage);
        t.setMilestone(milestone);
        return t;
    }

    private void openProjectDetail(Project project) {
        navigateTo("/projects/" + project.getCode());
        waitForPageLoad();
        assertThat(page.locator("[data-testid='project-detail'] h2")).hasText(project.getName());
    }

    private void clickMilestoneAction(ProjectMilestone milestone, String action) {
        page.locator("[data-milestone-id='" + milestone.getId() + "'] form[action$='/" + action + "'] button").click();
        waitForPageLoad();
    }

    private void assertBadges(ProjectMilestone milestone, String... badges) {
        assertThat(page.locator("[data-milestone-id='" + milestone.getId() + "'] span.text-xs.rounded-full"))
                .hasText(badges);
    }

    private Locator milestoneSection() {
        return page.locator("[data-testid='milestone-section']");
    }

    private Locator termSection() {
        return page.locator("[data-testid='payment-terms-section']");
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
