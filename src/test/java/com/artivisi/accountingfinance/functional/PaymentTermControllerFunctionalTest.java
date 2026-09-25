package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.entity.Project;
import com.artivisi.accountingfinance.entity.ProjectMilestone;
import com.artivisi.accountingfinance.entity.ProjectPaymentTerm;
import com.artivisi.accountingfinance.enums.InvoiceStatus;
import com.artivisi.accountingfinance.enums.PaymentTrigger;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ClientRepository;
import com.artivisi.accountingfinance.repository.InvoiceRepository;
import com.artivisi.accountingfinance.repository.ProjectPaymentTermRepository;
import com.artivisi.accountingfinance.service.ProjectMilestoneService;
import com.artivisi.accountingfinance.service.ProjectPaymentTermService;
import com.artivisi.accountingfinance.service.ProjectService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PaymentTermController: new/edit form, create/update with validation, delete, and invoice
 * generation (success and client-less project rejection).
 *
 * Each test creates its own project (unique code), so sequences and term lists are exact
 * regardless of what other test classes add to the shared database. Milestone-linked terms
 * and milestone-driven invoice due dates are covered by ServiceMilestonePaymentTermTest.
 */
@DisplayName("Payment Term Controller Tests")
@Import(ServiceTestDataInitializer.class)
class PaymentTermControllerFunctionalTest extends PlaywrightTestBase {

    // From src/test/resources/testdata/service/clients.csv
    private static final String CLIENT_CODE = "MANDIRI";
    private static final String CLIENT_NAME = "PT Bank Mandiri Tbk";
    private static final DateTimeFormatter DISPLAY_DATE =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.forLanguageTag("id-ID"));

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

    private Project project;

    @BeforeEach
    void setupAndLogin() {
        project = createProject(true);
        loginAsAdmin();
    }

    @Test
    @DisplayName("Should open empty new payment term form with triggers and project milestones")
    void shouldDisplayNewPaymentTermForm() {
        ProjectMilestone milestone = createMilestone(project, "Go Live " + shortId());
        createMilestone(createProject(true), "Other Project Milestone");

        navigateTo("/projects/" + project.getCode());
        waitForPageLoad();
        page.locator("#btn-new-payment-term").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/projects/" + project.getCode() + "/payment-terms/new", page.url());
        assertThat(page.locator("#page-title")).hasText("Termin Pembayaran Baru");
        assertThat(page.locator("nav a[href='/projects/" + project.getCode() + "']")).hasText(project.getName());
        assertThat(form()).hasAttribute("action", "/projects/" + project.getCode() + "/payment-terms/new");
        assertThat(page.locator("#sequence")).hasValue("");
        assertThat(page.locator("#name")).hasValue("");
        assertThat(page.locator("#dueTrigger")).hasValue("");
        assertThat(page.locator("#dueTrigger option")).hasText(new String[] {
                "-- Pilih Pemicu --", "Saat Penandatanganan", "Saat Milestone Selesai",
                "Saat Proyek Selesai", "Tanggal Tetap"});
        assertThat(page.locator("#milestone option")).hasText(new String[] {
                "-- Pilih Milestone --", "1. " + milestone.getName()});
        assertThat(page.locator("#milestone")).hasValue("");
        assertThat(page.locator("#dueDate")).hasValue("");
        assertThat(page.locator("#percentage")).hasValue("");
        assertThat(page.locator("#amount")).hasValue("");
    }

    @Test
    @DisplayName("Should create fixed-date term with nominal amount")
    void shouldCreateFixedAmountTerm() {
        String name = "Pelunasan " + shortId();
        LocalDate dueDate = LocalDate.of(2027, 3, 15);

        openNewForm();
        page.locator("#sequence").fill("1");
        page.locator("#name").fill(name);
        page.locator("#dueTrigger").selectOption("FIXED_DATE");
        page.locator("#dueDate").fill(dueDate.toString());
        page.locator("#amount").fill("150000000");
        submit();

        assertEquals(baseUrl() + "/projects/" + project.getCode(), page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Termin pembayaran berhasil ditambahkan");

        List<ProjectPaymentTerm> terms = terms();
        assertEquals(1, terms.size());
        ProjectPaymentTerm saved = terms.get(0);
        assertEquals(1, saved.getSequence());
        assertEquals(name, saved.getName());
        assertEquals(PaymentTrigger.FIXED_DATE, saved.getDueTrigger());
        assertEquals(dueDate, saved.getDueDate());
        assertEquals(0, new BigDecimal("150000000").compareTo(saved.getAmount()));
        assertNull(saved.getPercentage());

        Locator row = termRow(saved.getId());
        assertThat(row.locator("p.font-medium")).hasText(name);
        assertThat(row).containsText("Rp 150,000,000");
        assertThat(row).containsText("Tanggal Tetap");
        assertThat(row).containsText(DISPLAY_DATE.format(dueDate));
    }

    @Test
    @DisplayName("Should create percentage term with next sequence when sequence is blank")
    void shouldCreatePercentageTermWithAutoSequence() {
        createTerm(1, "DP " + shortId(), PaymentTrigger.ON_SIGNING, new BigDecimal("30"), null, null);
        String name = "Termin Kedua " + shortId();

        openNewForm();
        page.locator("#name").fill(name);
        page.locator("#dueTrigger").selectOption("ON_COMPLETION");
        page.locator("#percentage").fill("70");
        submit();

        assertThat(page.locator("[data-testid='flash-success']")).hasText("Termin pembayaran berhasil ditambahkan");
        ProjectPaymentTerm saved = terms().stream()
                .filter(t -> name.equals(t.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Payment term not persisted: " + name));
        assertEquals(2, saved.getSequence());
        assertEquals(0, new BigDecimal("70").compareTo(saved.getPercentage()));
        assertNull(saved.getAmount());

        Locator row = termRow(saved.getId());
        assertThat(row.locator("span.rounded-full")).hasText("2");
        assertThat(row).containsText("70.00% dari kontrak");
        assertThat(row).containsText("Saat Proyek Selesai");
    }

    @Test
    @DisplayName("Should reject term without trigger and persist nothing")
    void shouldRejectTermWithoutTrigger() {
        openNewForm();
        disableBrowserValidation();
        page.locator("#name").fill("No Trigger");
        page.locator("#percentage").fill("10");
        submit();

        assertThat(page.locator("#page-title")).hasText("Termin Pembayaran Baru");
        assertThat(page.locator("#dueTrigger ~ p.text-red-500")).hasText("Pemicu pembayaran wajib diisi");
        assertThat(page.locator("#name")).hasValue("No Trigger");
        assertTrue(terms().isEmpty(), "Payment term persisted despite missing trigger");
    }

    @Test
    @DisplayName("Should reject term without name and persist nothing")
    void shouldRejectTermWithoutName() {
        openNewForm();
        disableBrowserValidation();
        page.locator("#dueTrigger").selectOption("ON_SIGNING");
        page.locator("#percentage").fill("10");
        submit();

        assertThat(page.locator("#page-title")).hasText("Termin Pembayaran Baru");
        assertThat(page.locator("#name + p.text-red-500")).hasText("Nama termin wajib diisi");
        assertTrue(terms().isEmpty(), "Payment term persisted despite blank name");
    }

    @Test
    @DisplayName("Should reject duplicate sequence and keep existing term only")
    void shouldRejectDuplicateSequence() {
        ProjectPaymentTerm existing = createTerm(1, "DP " + shortId(), PaymentTrigger.ON_SIGNING,
                new BigDecimal("30"), null, null);

        openNewForm();
        page.locator("#sequence").fill("1");
        page.locator("#name").fill("Duplicate Sequence");
        page.locator("#dueTrigger").selectOption("ON_COMPLETION");
        page.locator("#amount").fill("50000000");
        submit();

        assertThat(page.locator("#page-title")).hasText("Termin Pembayaran Baru");
        assertThat(page.locator("#sequence ~ p.text-red-500")).hasText("Payment term with sequence 1 already exists");
        List<ProjectPaymentTerm> terms = terms();
        assertEquals(1, terms.size());
        assertEquals(existing.getId(), terms.get(0).getId());
    }

    @Test
    @DisplayName("Should open edit form prefilled with term values")
    void shouldDisplayEditFormPrefilled() {
        ProjectMilestone milestone = createMilestone(project, "UAT " + shortId());
        ProjectPaymentTerm term = createTerm(2, "Termin UAT " + shortId(), PaymentTrigger.ON_MILESTONE,
                new BigDecimal("40"), null, milestone, LocalDate.of(2027, 6, 30));

        navigateTo("/projects/" + project.getCode());
        waitForPageLoad();
        termRow(term.getId()).locator("a[title='Edit']").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/projects/" + project.getCode() + "/payment-terms/" + term.getId() + "/edit",
                page.url());
        assertThat(page.locator("#page-title")).hasText("Edit Termin Pembayaran");
        assertThat(form()).hasAttribute("action",
                "/projects/" + project.getCode() + "/payment-terms/" + term.getId());
        assertThat(page.locator("#sequence")).hasValue("2");
        assertThat(page.locator("#name")).hasValue(term.getName());
        assertThat(page.locator("#dueTrigger")).hasValue("ON_MILESTONE");
        assertThat(page.locator("#milestone")).hasValue(milestone.getId().toString());
        assertThat(page.locator("#dueDate")).hasValue("2027-06-30");
        assertThat(page.locator("#percentage")).hasValue("40.00");
        assertThat(page.locator("#amount")).hasValue("");
    }

    @Test
    @DisplayName("Should update term fields and unlink milestone")
    void shouldUpdatePaymentTerm() {
        ProjectMilestone milestone = createMilestone(project, "UAT " + shortId());
        ProjectPaymentTerm term = createTerm(1, "Termin " + shortId(), PaymentTrigger.ON_MILESTONE,
                new BigDecimal("40"), null, milestone);
        String newName = "Termin Tetap " + shortId();

        openEditForm(term.getId());
        page.locator("#sequence").fill("5");
        page.locator("#name").fill(newName);
        page.locator("#dueTrigger").selectOption("FIXED_DATE");
        page.locator("#milestone").selectOption("");
        page.locator("#dueDate").fill("2027-06-30");
        page.locator("#percentage").fill("");
        page.locator("#amount").fill("25000000");
        submit();

        assertEquals(baseUrl() + "/projects/" + project.getCode(), page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Termin pembayaran berhasil diperbarui");

        ProjectPaymentTerm updated = paymentTermRepository.findById(term.getId()).orElseThrow();
        assertEquals(5, updated.getSequence());
        assertEquals(newName, updated.getName());
        assertEquals(PaymentTrigger.FIXED_DATE, updated.getDueTrigger());
        assertEquals(LocalDate.of(2027, 6, 30), updated.getDueDate());
        assertNull(updated.getPercentage());
        assertEquals(0, new BigDecimal("25000000").compareTo(updated.getAmount()));
        assertTrue(paymentTermRepository.findByMilestoneId(milestone.getId()).isEmpty(),
                "Payment term still linked to milestone");

        Locator row = termRow(term.getId());
        assertThat(row.locator("p.font-medium")).hasText(newName);
        assertThat(row).containsText("Rp 25,000,000");
        assertThat(row).containsText("Tanggal Tetap");
        assertThat(row).not().containsText("Milestone:");
    }

    @Test
    @DisplayName("Should delete term after confirmation")
    void shouldDeletePaymentTerm() {
        ProjectPaymentTerm term = createTerm(1, "Hapus " + shortId(), PaymentTrigger.ON_SIGNING,
                new BigDecimal("10"), null, null);

        navigateTo("/projects/" + project.getCode());
        waitForPageLoad();
        page.onceDialog(dialog -> {
            assertEquals("Hapus termin pembayaran ini?", dialog.message());
            dialog.accept();
        });
        termRow(term.getId()).locator("form[action$='/delete'] button").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/projects/" + project.getCode(), page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Termin pembayaran berhasil dihapus");
        assertThat(termRow(term.getId())).hasCount(0);
        assertThat(page.locator("[data-testid='payment-terms-section']")).containsText("Belum ada termin pembayaran");
        assertTrue(paymentTermRepository.findById(term.getId()).isEmpty(), "Payment term still persisted");
    }

    @Test
    @DisplayName("Should generate draft invoice from fixed-date term")
    void shouldGenerateInvoiceFromPaymentTerm() {
        LocalDate dueDate = LocalDate.now().plusDays(45);
        ProjectPaymentTerm term = createTerm(1, "Termin Invoice " + shortId(), PaymentTrigger.FIXED_DATE,
                null, new BigDecimal("12500000"), null, dueDate);

        navigateTo("/projects/" + project.getCode());
        waitForPageLoad();
        page.onceDialog(dialog -> {
            assertEquals("Buat invoice dari termin ini?", dialog.message());
            dialog.accept();
        });
        termRow(term.getId()).locator("form[action$='/generate-invoice'] button").click();
        waitForPageLoad();

        List<Invoice> invoices = invoiceRepository.findByPaymentTermId(term.getId());
        assertEquals(1, invoices.size());
        Invoice invoice = invoices.get(0);
        assertEquals(InvoiceStatus.DRAFT, invoice.getStatus());
        assertEquals(LocalDate.now(), invoice.getInvoiceDate());
        assertEquals(dueDate, invoice.getDueDate());
        assertEquals(0, new BigDecimal("12500000").compareTo(invoice.getAmount()));
        assertEquals("Generated from payment term: " + term.getName(), invoice.getNotes());
        assertEquals(1, invoiceRepository.countByProjectId(project.getId()));

        assertEquals(baseUrl() + "/invoices/" + invoice.getInvoiceNumber(), page.url());
        assertThat(page.locator("[data-testid='flash-success']"))
                .hasText("Invoice berhasil dibuat dari termin pembayaran");
        assertThat(page.locator("[data-testid='invoice-status']")).hasText("Draf");
        Locator detail = page.locator("[data-testid='invoice-detail']");
        assertThat(detail).containsText(invoice.getInvoiceNumber());
        assertThat(detail).containsText(CLIENT_NAME);
        assertThat(detail).containsText(project.getName());
        assertThat(detail).containsText("Generated from payment term: " + term.getName());
    }

    @Test
    @DisplayName("Should refuse invoice generation for project without client")
    void shouldRejectInvoiceGenerationWithoutClient() {
        Project noClient = createProject(false);
        ProjectPaymentTerm term = paymentTermService.create(noClient.getId(),
                newTerm(1, "Tanpa Klien " + shortId(), PaymentTrigger.ON_SIGNING, new BigDecimal("10"), null, null, null));

        navigateTo("/projects/" + noClient.getCode());
        waitForPageLoad();
        assertThat(page.locator("[data-testid='payment-terms-section']"))
                .containsText("Pilih klien terlebih dahulu untuk membuat termin pembayaran");
        assertThat(page.locator("form[action$='/generate-invoice']")).hasCount(0);

        String csrfHeader = page.locator("meta[name='_csrf_header']").getAttribute("content");
        String csrfToken = page.locator("meta[name='_csrf']").getAttribute("content");
        APIResponse response = page.request().post(
                baseUrl() + "/projects/" + noClient.getCode() + "/payment-terms/" + term.getId() + "/generate-invoice",
                RequestOptions.create().setHeader(csrfHeader, csrfToken).setMaxRedirects(0));

        assertEquals(302, response.status());
        assertEquals(baseUrl() + "/projects/" + noClient.getCode(), response.headers().get("location"));
        assertTrue(invoiceRepository.findByPaymentTermId(term.getId()).isEmpty(), "Invoice created without client");

        navigateTo("/projects/" + noClient.getCode());
        waitForPageLoad();
        assertThat(page.locator("[data-testid='flash-error']")).hasText("Project must have a client to create invoice");
    }

    // ==================== helpers ====================

    private Project createProject(boolean withClient) {
        Project p = new Project();
        p.setCode("PT-" + shortId());
        p.setName("Proyek Termin " + shortId());
        p.setContractValue(new BigDecimal("100000000"));
        UUID clientId = withClient
                ? clientRepository.findByCode(CLIENT_CODE)
                        .orElseThrow(() -> new IllegalStateException("Seeded client missing: " + CLIENT_CODE))
                        .getId()
                : null;
        return projectService.create(p, clientId);
    }

    private ProjectMilestone createMilestone(Project target, String name) {
        ProjectMilestone m = new ProjectMilestone();
        m.setSequence(1);
        m.setName(name);
        m.setWeightPercent(100);
        return milestoneService.create(target.getId(), m);
    }

    private ProjectPaymentTerm createTerm(int sequence, String name, PaymentTrigger trigger, BigDecimal percentage,
                                          BigDecimal amount, ProjectMilestone milestone) {
        return createTerm(sequence, name, trigger, percentage, amount, milestone, null);
    }

    private ProjectPaymentTerm createTerm(int sequence, String name, PaymentTrigger trigger, BigDecimal percentage,
                                          BigDecimal amount, ProjectMilestone milestone, LocalDate dueDate) {
        return paymentTermService.create(project.getId(),
                newTerm(sequence, name, trigger, percentage, amount, milestone, dueDate));
    }

    private static ProjectPaymentTerm newTerm(int sequence, String name, PaymentTrigger trigger, BigDecimal percentage,
                                              BigDecimal amount, ProjectMilestone milestone, LocalDate dueDate) {
        ProjectPaymentTerm t = new ProjectPaymentTerm();
        t.setSequence(sequence);
        t.setName(name);
        t.setDueTrigger(trigger);
        t.setPercentage(percentage);
        t.setAmount(amount);
        t.setMilestone(milestone);
        t.setDueDate(dueDate);
        return t;
    }

    private List<ProjectPaymentTerm> terms() {
        return paymentTermRepository.findByProjectIdOrderBySequenceAsc(project.getId());
    }

    private void openNewForm() {
        navigateTo("/projects/" + project.getCode() + "/payment-terms/new");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Termin Pembayaran Baru");
    }

    private void openEditForm(UUID termId) {
        navigateTo("/projects/" + project.getCode() + "/payment-terms/" + termId + "/edit");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Edit Termin Pembayaran");
    }

    private Locator form() {
        return page.locator("form:has(#btn-simpan)");
    }

    private void disableBrowserValidation() {
        page.locator("#btn-simpan").evaluate("b => b.form.noValidate = true");
    }

    private void submit() {
        page.locator("#btn-simpan").click();
        waitForPageLoad();
    }

    private Locator termRow(UUID termId) {
        return page.locator("[data-payment-term-id='" + termId + "']");
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
