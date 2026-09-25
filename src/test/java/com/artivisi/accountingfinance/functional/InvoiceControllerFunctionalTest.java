package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.Client;
import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.entity.InvoiceLine;
import com.artivisi.accountingfinance.entity.InvoicePayment;
import com.artivisi.accountingfinance.entity.Project;
import com.artivisi.accountingfinance.enums.InvoiceStatus;
import com.artivisi.accountingfinance.enums.PaymentMethod;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.InvoiceRepository;
import com.artivisi.accountingfinance.repository.ProjectRepository;
import com.artivisi.accountingfinance.service.ClientService;
import com.artivisi.accountingfinance.service.InvoiceService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.artivisi.accountingfinance.util.AmountToWordsUtil;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * InvoiceController list/filter/pagination, state guards on edit/pay/send/cancel/delete/mark-paid,
 * the mark-paid endpoint, print view and 404 handling. UI create/edit/send/cancel/delete flows
 * are covered by ServiceInvoiceTest; payment recording by PaymentTrackingTest.
 *
 * Each test creates its own client (unique code) and filters the list by it, so row
 * assertions are exact regardless of invoices other test classes add to the shared database.
 */
@DisplayName("Invoice Controller Tests")
@Import(ServiceTestDataInitializer.class)
class InvoiceControllerFunctionalTest extends PlaywrightTestBase {

    // From src/test/resources/testdata/service/projects.csv
    private static final String PROJECT_CODE = "PRJ-2024-003";

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private ClientService clientService;

    @Autowired
    private ProjectRepository projectRepository;

    private Client client;
    private String csrfHeader;
    private String csrfToken;

    @BeforeEach
    void setupAndLogin() {
        client = createClient();
        loginAsAdmin();
        csrfHeader = page.locator("meta[name='_csrf_header']").getAttribute("content");
        csrfToken = page.locator("meta[name='_csrf']").getAttribute("content");
    }

    // ==================== List ====================

    @Test
    @DisplayName("Should display invoice list with summary counts and invoice rows")
    void shouldDisplayInvoiceListPage() {
        Project project = seededProject();
        Invoice invoice = createInvoice(LocalDate.now(), project);

        navigateTo("/invoices");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Daftar Invoice");
        Locator cards = page.locator("[data-testid='invoice-list'] > div").first();
        assertThat(cards.locator("div:has(> p:text-is('Draf')) > p").nth(1))
                .hasText(String.valueOf(invoiceRepository.countByStatus(InvoiceStatus.DRAFT)));
        assertThat(cards.locator("div:has(> p:text-is('Terkirim')) > p").nth(1))
                .hasText(String.valueOf(invoiceRepository.countByStatus(InvoiceStatus.SENT)));
        assertThat(cards.locator("div:has(> p:text-is('Jatuh Tempo')) > p").nth(1))
                .hasText(String.valueOf(invoiceRepository.countByStatus(InvoiceStatus.OVERDUE)));
        assertThat(cards.locator("div:has(> p:text-is('Lunas')) > p").nth(1))
                .hasText(String.valueOf(invoiceRepository.countByStatus(InvoiceStatus.PAID)));
        assertThat(page.locator("#btn-new-invoice")).hasAttribute("href", "/invoices/new");

        navigateTo("/invoices?clientId=" + client.getId());
        waitForPageLoad();

        Locator row = rows().first();
        assertThat(rows()).hasCount(1);
        assertThat(row.locator("td").nth(0)).hasText(invoice.getInvoiceNumber());
        assertThat(row.locator("td").nth(0).locator("a"))
                .hasAttribute("href", "/invoices/" + invoice.getInvoiceNumber());
        assertThat(row.locator("td").nth(1)).hasText(client.getName());
        assertThat(row.locator("td").nth(2)).hasText(project.getName());
        assertThat(row.locator("td").nth(5)).hasText("Rp 2,500,000");
        assertThat(row.locator("td").nth(6)).hasText("Draf");
    }

    @Test
    @DisplayName("Should show empty state when no invoice matches the filter")
    void shouldShowEmptyStateWhenNoInvoiceMatches() {
        navigateTo("/invoices?clientId=" + client.getId());
        waitForPageLoad();

        assertThat(page.locator("[data-testid='invoice-table'] tbody tr")).hasCount(1);
        assertThat(page.locator("[data-testid='invoice-table'] tbody td")).hasText("Belum ada invoice");
    }

    @Test
    @DisplayName("Should filter invoices by status and client through the filter form")
    void shouldFilterInvoicesByStatusAndClientViaForm() {
        Invoice draft = createInvoice(LocalDate.now(), null);
        Invoice sent = createInvoice(LocalDate.now(), null);
        invoiceService.send(sent.getId());

        navigateTo("/invoices");
        waitForPageLoad();
        page.locator("#status-filter").selectOption("SENT");
        Locator clientInput = page.locator("#client-picker-input");
        clientInput.click();
        clientInput.fill(client.getCode());
        Locator result = page.locator("[data-testid='client-picker-result']",
                new Page.LocatorOptions().setHasText(client.getCode()));
        assertThat(result).hasCount(1);
        result.click();
        page.locator("form[action='/invoices'] button[type='submit']").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/invoices?status=SENT&clientId=" + client.getId() + "&projectId=");
        assertThat(rows()).hasCount(1);
        assertThat(rows().first().locator("td").nth(0)).hasText(sent.getInvoiceNumber());
        assertThat(rows().first().locator("td").nth(6)).hasText("Terkirim");
        assertThat(page.locator("[data-testid='invoice-table']")).not().containsText(draft.getInvoiceNumber());
        assertThat(page.locator("#status-filter")).hasValue("SENT");
        assertThat(clientInput).hasValue(client.getCode() + " - " + client.getName());
    }

    @Test
    @DisplayName("Should filter invoices by each status")
    void shouldFilterInvoicesByEachStatus() {
        Invoice draft = createInvoice(LocalDate.now(), null);
        Invoice sent = createInvoice(LocalDate.now(), null);
        invoiceService.send(sent.getId());
        Invoice partial = createInvoice(LocalDate.now(), null);
        invoiceService.send(partial.getId());
        invoiceService.recordPayment(partial.getId(), payment("1000000"));
        Invoice paid = createInvoice(LocalDate.now(), null);
        invoiceService.send(paid.getId());
        invoiceService.markAsPaid(paid.getId());
        Invoice cancelled = createInvoice(LocalDate.now(), null);
        invoiceService.cancel(cancelled.getId());

        Map<String, Invoice> expected = Map.of(
                "DRAFT", draft, "SENT", sent, "PARTIAL", partial, "PAID", paid, "CANCELLED", cancelled);
        Map<String, String> badges = Map.of(
                "DRAFT", "Draf", "SENT", "Terkirim", "PARTIAL", "Sebagian", "PAID", "Lunas", "CANCELLED", "Dibatalkan");

        for (var entry : expected.entrySet()) {
            navigateTo("/invoices?status=" + entry.getKey() + "&clientId=" + client.getId());
            waitForPageLoad();

            assertThat(rows()).hasCount(1);
            assertThat(rows().first().locator("td").nth(0)).hasText(entry.getValue().getInvoiceNumber());
            assertThat(rows().first().locator("td").nth(6)).hasText(badges.get(entry.getKey()));
            assertThat(page.locator("#status-filter")).hasValue(entry.getKey());
        }

        navigateTo("/invoices?status=OVERDUE&clientId=" + client.getId());
        waitForPageLoad();
        assertThat(page.locator("[data-testid='invoice-table'] tbody td")).hasText("Belum ada invoice");
    }

    @Test
    @DisplayName("Should filter invoices by project through the filter form")
    void shouldFilterInvoicesByProject() {
        Project project = seededProject();
        Invoice withProject = createInvoice(LocalDate.now(), project);
        Invoice withoutProject = createInvoice(LocalDate.now(), null);

        navigateTo("/invoices?clientId=" + client.getId());
        waitForPageLoad();
        assertThat(rows()).hasCount(2);

        page.locator("#project-filter").selectOption(project.getId().toString());
        page.locator("form[action='/invoices'] button[type='submit']").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/invoices?status=&clientId=" + client.getId() + "&projectId=" + project.getId());
        assertThat(rows()).hasCount(1);
        assertThat(rows().first().locator("td").nth(0)).hasText(withProject.getInvoiceNumber());
        assertThat(rows().first().locator("td").nth(2)).hasText(project.getName());
        assertThat(page.locator("[data-testid='invoice-table']")).not().containsText(withoutProject.getInvoiceNumber());
        assertThat(page.locator("#project-filter")).hasValue(project.getId().toString());
    }

    @Test
    @DisplayName("Should paginate invoice list 20 per page and keep filters in page links")
    void shouldPaginateInvoiceList() {
        LocalDate base = LocalDate.now();
        List<Invoice> invoices = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            invoices.add(createInvoice(base.minusDays(i), null));
        }
        Invoice oldest = invoices.get(20);

        navigateTo("/invoices?clientId=" + client.getId());
        waitForPageLoad();

        assertThat(rows()).hasCount(20);
        assertThat(rows().first().locator("td").nth(0)).hasText(invoices.get(0).getInvoiceNumber());
        assertThat(page.locator("[data-testid='invoice-table']")).not().containsText(oldest.getInvoiceNumber());
        assertThat(page.locator("text=Menampilkan")).hasText("Menampilkan 1 - 20 dari 21 invoice");
        assertThat(page.locator("a:has-text('Prev')")).hasCount(0);

        page.locator("a:has-text('Next')").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/invoices?page=1&status=&clientId=" + client.getId() + "&projectId=");
        assertThat(rows()).hasCount(1);
        assertThat(rows().first().locator("td").nth(0)).hasText(oldest.getInvoiceNumber());
        assertThat(page.locator("text=Menampilkan")).hasText("Menampilkan 21 - 21 dari 21 invoice");
        assertThat(page.locator("a:has-text('Next')")).hasCount(0);
        assertThat(page.locator("a:has-text('Prev')"))
                .hasAttribute("href", "/invoices?page=0&status=&clientId=" + client.getId() + "&projectId=");
    }

    // ==================== Detail / 404 ====================

    @Test
    @DisplayName("Should render 404 page for unknown invoice number")
    void shouldRender404ForUnknownInvoice() {
        var response = page.navigate(baseUrl() + "/invoices/NO-SUCH-" + shortId());

        assertEquals(404, response.status());
        assertThat(page.locator("body")).containsText("Halaman Tidak Ditemukan");
    }

    // ==================== State guards ====================

    @Test
    @DisplayName("Should redirect edit form to detail for non-draft invoices")
    void shouldRedirectEditFormForNonDraftInvoices() {
        Invoice sent = createInvoice(LocalDate.now(), null);
        invoiceService.send(sent.getId());
        Invoice paid = createInvoice(LocalDate.now(), null);
        invoiceService.send(paid.getId());
        invoiceService.markAsPaid(paid.getId());
        Invoice cancelled = createInvoice(LocalDate.now(), null);
        invoiceService.cancel(cancelled.getId());

        for (Invoice invoice : List.of(sent, paid, cancelled)) {
            navigateTo("/invoices/" + invoice.getInvoiceNumber() + "/edit");
            waitForPageLoad();

            assertThat(page).hasURL(baseUrl() + "/invoices/" + invoice.getInvoiceNumber());
            assertThat(page.locator("#page-title")).hasText("Detail Invoice");
            assertThat(page.locator("#btn-simpan")).hasCount(0);
        }
    }

    @Test
    @DisplayName("Should reject pay form for draft, cancelled and paid invoices")
    void shouldRejectPayFormForNonPayableInvoices() {
        Invoice draft = createInvoice(LocalDate.now(), null);
        Invoice cancelled = createInvoice(LocalDate.now(), null);
        invoiceService.cancel(cancelled.getId());
        Invoice paid = createInvoice(LocalDate.now(), null);
        invoiceService.send(paid.getId());
        invoiceService.markAsPaid(paid.getId());

        for (Invoice invoice : List.of(draft, cancelled, paid)) {
            navigateTo("/invoices/" + invoice.getInvoiceNumber() + "/pay");
            waitForPageLoad();

            assertThat(page).hasURL(baseUrl() + "/invoices/" + invoice.getInvoiceNumber());
            assertThat(page.locator("[data-testid='flash-error']"))
                    .hasText("Hanya invoice terkirim, jatuh tempo, atau sebagian yang dapat dibayar");
        }
    }

    @Test
    @DisplayName("Should reject send, delete, cancel and mark-paid when invoice status does not allow it")
    void shouldRejectInvalidStateTransitions() {
        Invoice sent = createInvoice(LocalDate.now(), null);
        invoiceService.send(sent.getId());
        Invoice paid = createInvoice(LocalDate.now(), null);
        invoiceService.send(paid.getId());
        invoiceService.markAsPaid(paid.getId());
        Invoice draft = createInvoice(LocalDate.now(), null);

        assertPostRejected(sent, "send", "Only draft invoices can be sent", InvoiceStatus.SENT);
        assertPostRejected(sent, "delete", "Only draft or cancelled invoices can be deleted", InvoiceStatus.SENT);
        assertPostRejected(paid, "cancel", "Paid invoices cannot be cancelled", InvoiceStatus.PAID);
        assertPostRejected(draft, "mark-paid", "Only sent, overdue, or partial invoices can be marked as paid",
                InvoiceStatus.DRAFT);
    }

    // ==================== Mark paid ====================

    @Test
    @DisplayName("Should mark sent invoice as paid")
    void shouldMarkSentInvoiceAsPaid() {
        Invoice invoice = createInvoice(LocalDate.now(), null);
        invoiceService.send(invoice.getId());

        APIResponse response = post(invoice, "mark-paid");

        assertEquals(200, response.status());
        assertEquals(baseUrl() + "/invoices/" + invoice.getInvoiceNumber(), response.url());
        page.setContent(response.text());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Invoice ditandai sudah dibayar");
        assertThat(page.locator("[data-testid='invoice-status']")).hasText("Lunas");

        Invoice updated = invoiceRepository.findByInvoiceNumber(invoice.getInvoiceNumber()).orElseThrow();
        assertEquals(InvoiceStatus.PAID, updated.getStatus());
        assertNotNull(updated.getPaidAt());
    }

    // ==================== Print ====================

    @Test
    @DisplayName("Should render print view with company, client, lines and total including PPN")
    void shouldDisplayPrintPreview() {
        Project project = seededProject();
        InvoiceLine line = new InvoiceLine();
        line.setDescription("Workshop transformasi digital");
        line.setQuantity(new BigDecimal("2"));
        line.setUnitPrice(new BigDecimal("5000000"));
        line.setTaxRate(new BigDecimal("11"));
        line.calculateAmounts();
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("INV-PRN-" + shortId());
        invoice.setClient(client);
        invoice.setProject(project);
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(30));
        invoice.setAmount(BigDecimal.ZERO);
        invoice.setNotes("Transfer ke rekening perusahaan");
        invoice = invoiceService.create(invoice, List.of(line));

        navigateTo("/invoices/" + invoice.getInvoiceNumber() + "/print");
        waitForPageLoad();

        assertThat(page).hasTitle("Invoice " + invoice.getInvoiceNumber());
        assertThat(page.locator(".company-name")).hasText("PT ArtiVisi Intermedia");
        assertThat(page.locator("body")).containsText("NPWP: 01.234.567.8-901.000");
        assertThat(page.locator(".invoice-number")).hasText(invoice.getInvoiceNumber());
        assertThat(page.locator(".status-badge")).hasText("Draf");
        assertThat(page.locator(".client-name")).hasText(client.getName());
        assertThat(page.locator(".project-name")).hasText(PROJECT_CODE + " - " + project.getName());

        Locator lineRow = page.locator(".line-items-section tbody tr");
        assertThat(lineRow).hasCount(1);
        assertThat(lineRow.locator("td").nth(1)).hasText("Workshop transformasi digital");
        assertThat(lineRow.locator("td").nth(2)).hasText("2.00");
        assertThat(lineRow.locator("td").nth(3)).hasText("Rp 5,000,000");
        assertThat(lineRow.locator("td").nth(4)).hasText("11.00%");
        assertThat(lineRow.locator("td").nth(5)).hasText("Rp 10,000,000");
        Locator footer = page.locator(".line-items-section tfoot tr");
        assertThat(footer.nth(0).locator("td").nth(1)).hasText("Rp 10,000,000");
        assertThat(footer.nth(1).locator("td").nth(1)).hasText("Rp 1,100,000");
        assertThat(footer.nth(2).locator("td").nth(1)).hasText("Rp 11,100,000");

        assertThat(page.locator(".amount-value")).hasText("Rp 11,100,000");
        assertThat(page.locator(".amount-words")).hasText(AmountToWordsUtil.toWords(new BigDecimal("11100000")));
        assertThat(page.locator(".notes-content")).hasText("Transfer ke rekening perusahaan");
        assertThat(page.locator(".signature-name")).hasText("Endy Muhardin");
        assertThat(page.locator("a.btn-back")).hasAttribute("href", "/invoices/" + invoice.getInvoiceNumber());
    }

    // ==================== helpers ====================

    private Locator rows() {
        return page.locator("[data-testid='invoice-table'] tbody tr:has(a)");
    }

    private void assertPostRejected(Invoice invoice, String action, String message, InvoiceStatus unchanged) {
        APIResponse response = post(invoice, action);

        assertEquals(200, response.status(), action);
        assertEquals(baseUrl() + "/invoices/" + invoice.getInvoiceNumber(), response.url(), action);
        page.setContent(response.text());
        assertThat(page.locator("[data-testid='flash-error']")).hasText(message);
        assertEquals(unchanged, invoiceRepository.findByInvoiceNumber(invoice.getInvoiceNumber())
                .orElseThrow(() -> new AssertionError("Invoice removed by rejected " + action))
                .getStatus(), action);
    }

    private APIResponse post(Invoice invoice, String action) {
        return page.request().post(baseUrl() + "/invoices/" + invoice.getInvoiceNumber() + "/" + action,
                RequestOptions.create().setHeader(csrfHeader, csrfToken));
    }

    private Client createClient() {
        Client c = new Client();
        c.setCode("INV-CT-" + shortId());
        c.setName("PT Klien Filter " + shortId());
        return clientService.create(c);
    }

    private Project seededProject() {
        return projectRepository.findByCode(PROJECT_CODE)
                .orElseThrow(() -> new IllegalStateException("Seeded project missing: " + PROJECT_CODE));
    }

    private Invoice createInvoice(LocalDate invoiceDate, Project project) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("INV-CT-" + shortId());
        invoice.setClient(client);
        invoice.setProject(project);
        invoice.setInvoiceDate(invoiceDate);
        invoice.setDueDate(invoiceDate.plusDays(30));
        invoice.setAmount(new BigDecimal("2500000"));
        return invoiceService.create(invoice);
    }

    private static InvoicePayment payment(String amount) {
        InvoicePayment payment = new InvoicePayment();
        payment.setPaymentDate(LocalDate.now());
        payment.setAmount(new BigDecimal(amount));
        payment.setPaymentMethod(PaymentMethod.TRANSFER);
        return payment;
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
