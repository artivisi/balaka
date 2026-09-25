package com.artivisi.accountingfinance.functional.service;

import com.artivisi.accountingfinance.entity.Client;
import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.entity.InvoiceLine;
import com.artivisi.accountingfinance.entity.Product;
import com.artivisi.accountingfinance.entity.Project;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.InvoiceStatus;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.InvoiceRepository;
import com.artivisi.accountingfinance.repository.ProductRepository;
import com.artivisi.accountingfinance.repository.ProjectRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.service.ClientService;
import com.artivisi.accountingfinance.service.InvoiceService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Invoice form and detail-page actions driven through the UI: create (manual/auto number, line
 * items), form validation, pre-selection, edit/update, detail content, send, cancel, delete.
 * List filtering, pagination, state-transition guards, pay redirect and print are covered by
 * InvoiceControllerFunctionalTest; payment recording by PaymentTrackingTest.
 *
 * Each test creates its own client (unique code) so repository lookups by client are exact
 * regardless of invoices created by other test classes sharing the database.
 */
@DisplayName("Service Industry - Invoice Management")
@Import(ServiceTestDataInitializer.class)
class ServiceInvoiceTest extends PlaywrightTestBase {

    // From src/test/resources/testdata/service/projects.csv
    private static final String PROJECT_CODE = "PRJ-2024-003";
    // From industry-seed/it-service/seed-data/02_chart_of_accounts.csv
    private static final String REVENUE_ACCOUNT = "4.1.01";

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private ClientService clientService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ChartOfAccountRepository chartOfAccountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private Client client;

    @BeforeEach
    void setup() {
        client = createClient();
        loginAsAdmin();
    }

    // ==================== Create Invoice ====================

    @Test
    @DisplayName("Should display new invoice form")
    void shouldDisplayNewInvoiceForm() {
        navigateTo("/invoices/new");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Invoice Baru");
        assertThat(page.locator("#invoiceNumber")).hasValue("");
        assertThat(page.locator("#invoiceNumber")).hasAttribute("placeholder", "Auto-generated jika kosong");
        assertThat(page.locator("#clientLabel")).hasValue("");
        assertThat(page.locator("#project")).hasValue("");
        assertThat(page.locator("#project option", new Page.LocatorOptions()
                .setHasText(PROJECT_CODE + " - Digital Transformation Roadmap"))).hasCount(1);
        assertThat(page.locator("#invoiceDate")).isVisible();
        assertThat(page.locator("#dueDate")).isVisible();
        assertThat(page.locator("#amount")).isVisible();
        assertThat(page.locator("#notes")).isVisible();
        assertThat(page.locator("form[action='/invoices/new'] #btn-simpan")).isVisible();
    }

    @Test
    @DisplayName("Should create invoice with manual number")
    void shouldCreateInvoiceWithManualNumber() {
        String invoiceNumber = "INV-MAN-" + shortId();
        LocalDate invoiceDate = LocalDate.now();
        LocalDate dueDate = invoiceDate.plusDays(30);

        navigateTo("/invoices/new");
        waitForPageLoad();
        page.locator("#invoiceNumber").fill(invoiceNumber);
        selectClient();
        page.locator("#invoiceDate").fill(invoiceDate.toString());
        page.locator("#dueDate").fill(dueDate.toString());
        page.locator("#amount").fill("5000000");
        page.locator("#notes").fill("Catatan invoice manual");
        page.click("#btn-simpan");
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/invoices/" + invoiceNumber);
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Invoice berhasil dibuat");
        assertThat(detail().locator("h2")).hasText(invoiceNumber);
        assertThat(detail()).containsText(client.getName());
        assertThat(detail()).containsText("Catatan invoice manual");

        Invoice saved = invoiceRepository.findByInvoiceNumber(invoiceNumber)
                .orElseThrow(() -> new AssertionError("Invoice not persisted: " + invoiceNumber));
        assertEquals(InvoiceStatus.DRAFT, saved.getStatus());
        assertEquals(client.getId(), saved.getClient().getId());
        assertEquals(0, new BigDecimal("5000000").compareTo(saved.getAmount()));
        assertEquals(invoiceDate, saved.getInvoiceDate());
        assertEquals(dueDate, saved.getDueDate());
        assertEquals("Catatan invoice manual", saved.getNotes());
        assertNull(saved.getProject());
    }

    @Test
    @DisplayName("Should create invoice with auto-generated number")
    void shouldCreateInvoiceWithAutoNumber() {
        String expectedPrefix = "INV-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-";

        navigateTo("/invoices/new");
        waitForPageLoad();
        selectClient();
        page.locator("#invoiceDate").fill(LocalDate.now().toString());
        page.locator("#dueDate").fill(LocalDate.now().plusDays(30).toString());
        page.locator("#amount").fill("7500000");
        page.click("#btn-simpan");
        waitForPageLoad();

        assertThat(page).hasURL(Pattern.compile(".*/invoices/" + expectedPrefix + "\\d{4}$"));
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Invoice berhasil dibuat");

        List<Invoice> invoices = invoiceRepository.findByClientId(client.getId());
        assertEquals(1, invoices.size());
        Invoice saved = invoices.get(0);
        assertTrue(saved.getInvoiceNumber().matches(Pattern.quote(expectedPrefix) + "\\d{4}"),
                "Unexpected generated number: " + saved.getInvoiceNumber());
        assertEquals(page.url(), baseUrl() + "/invoices/" + saved.getInvoiceNumber());
        assertEquals(0, new BigDecimal("7500000").compareTo(saved.getAmount()));
    }

    @Test
    @DisplayName("Should create invoice with line items and compute amount and tax from lines")
    void shouldCreateInvoiceWithLineItems() {
        String invoiceNumber = "INV-LINE-" + shortId();

        navigateTo("/invoices/new");
        waitForPageLoad();
        page.locator("#invoiceNumber").fill(invoiceNumber);
        selectClient();
        page.locator("#invoiceDate").fill(LocalDate.now().toString());
        page.locator("#dueDate").fill(LocalDate.now().plusDays(14).toString());
        page.locator("button:has-text('+ Tambah Item')").click();
        page.locator("#inv-lineDescription-0").fill("Konsultasi arsitektur");
        page.locator("#inv-lineQuantity-0").fill("3");
        page.locator("#inv-lineUnitPrice-0").fill("2000000");
        page.locator("#inv-lineTaxRate-0").fill("11");
        assertThat(page.locator("#amount")).isHidden();
        page.click("#btn-simpan");
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/invoices/" + invoiceNumber);
        Locator lineRow = detail().locator("tbody tr", new Locator.LocatorOptions().setHasText("Konsultasi arsitektur"));
        assertThat(lineRow).hasCount(1);
        assertThat(lineRow).containsText("3.00");
        assertThat(lineRow).containsText("Rp 2,000,000");
        assertThat(lineRow).containsText("11.00%");
        assertThat(lineRow).containsText("Rp 6,000,000");

        Invoice saved = invoiceRepository.findByInvoiceNumber(invoiceNumber)
                .orElseThrow(() -> new AssertionError("Invoice not persisted: " + invoiceNumber));
        assertEquals(0, new BigDecimal("6000000").compareTo(saved.getAmount()));
        assertEquals(0, new BigDecimal("660000").compareTo(saved.getTaxAmount()));
        assertEquals(0, new BigDecimal("6660000").compareTo(saved.getTotalAmount()));
        assertThat(detail()).containsText("Rp 6,660,000");
    }

    @Test
    @DisplayName("Should reject duplicate invoice number without creating another invoice")
    void shouldRejectDuplicateInvoiceNumber() {
        String invoiceNumber = createDraftInvoice(null).getInvoiceNumber();

        navigateTo("/invoices/new");
        waitForPageLoad();
        page.locator("#invoiceNumber").fill(invoiceNumber);
        selectClient();
        page.locator("#invoiceDate").fill(LocalDate.now().toString());
        page.locator("#dueDate").fill(LocalDate.now().plusDays(30).toString());
        page.locator("#amount").fill("1000000");
        page.click("#btn-simpan");
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/invoices/new");
        assertThat(page.locator("form li")).hasText("Invoice number already exists: " + invoiceNumber);
        assertEquals(1, invoiceRepository.findByClientId(client.getId()).size());
    }

    @Test
    @DisplayName("Should show validation error when client typed but not selected")
    void shouldShowValidationErrorForMissingClient() {
        navigateTo("/invoices/new");
        waitForPageLoad();
        page.locator("#clientLabel").fill(client.getCode());
        page.locator("#invoiceDate").click();
        page.locator("#invoiceDate").fill(LocalDate.now().toString());
        page.locator("#dueDate").fill(LocalDate.now().plusDays(30).toString());
        page.locator("#amount").fill("1000000");
        page.locator("#notes").fill("missing-client-" + client.getCode());
        page.click("#btn-simpan");
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/invoices/new");
        assertThat(page.locator("form li")).hasText("Klien wajib diisi");
        assertTrue(invoiceRepository.findAll().stream()
                        .noneMatch(i -> ("missing-client-" + client.getCode()).equals(i.getNotes())),
                "Invoice without client was persisted");
    }

    @Test
    @DisplayName("Should show validation error for missing amount and persist nothing")
    void shouldShowValidationErrorForMissingAmount() {
        navigateTo("/invoices/new");
        waitForPageLoad();
        selectClient();
        page.locator("#invoiceDate").fill(LocalDate.now().toString());
        page.locator("#dueDate").fill(LocalDate.now().plusDays(30).toString());
        page.click("#btn-simpan");
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/invoices/new");
        assertThat(page.locator("form li")).hasText("Jumlah wajib diisi");
        assertTrue(invoiceRepository.findByClientId(client.getId()).isEmpty(), "Invoice without amount was persisted");
    }

    @Test
    @DisplayName("Should pre-select client and project from query params")
    void shouldPreSelectClientAndProjectFromQueryParams() {
        Project project = seededProject();

        navigateTo("/invoices/new?clientId=" + client.getId() + "&projectId=" + project.getId());
        waitForPageLoad();

        assertThat(page.locator("#clientLabel")).hasValue(client.getCode() + " - " + client.getName());
        assertThat(page.locator("input[type='hidden'][name='client.id']")).hasValue(client.getId().toString());
        assertThat(page.locator("#project")).hasValue(project.getId().toString());
    }

    // ==================== Edit Invoice ====================

    @Test
    @DisplayName("Should display edit form prefilled with draft invoice values")
    void shouldDisplayEditFormForDraftInvoice() {
        Project project = seededProject();
        Invoice invoice = createDraftInvoice(project);

        navigateTo("/invoices/" + invoice.getInvoiceNumber() + "/edit");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Edit Invoice");
        assertThat(page.locator("form[action='/invoices/" + invoice.getInvoiceNumber() + "']")).hasCount(1);
        assertThat(page.locator("#invoiceNumber")).hasValue(invoice.getInvoiceNumber());
        assertThat(page.locator("#clientLabel")).hasValue(client.getCode() + " - " + client.getName());
        assertThat(page.locator("input[type='hidden'][name='client.id']")).hasValue(client.getId().toString());
        assertThat(page.locator("#project")).hasValue(project.getId().toString());
        assertThat(page.locator("#invoiceDate")).hasValue(invoice.getInvoiceDate().toString());
        assertThat(page.locator("#dueDate")).hasValue(invoice.getDueDate().toString());
        assertThat(page.locator("#amount")).hasValue("2500000.00");
        assertThat(page.locator("#notes")).hasValue(invoice.getNotes());
    }

    @Test
    @DisplayName("Should update draft invoice")
    void shouldUpdateDraftInvoice() {
        Invoice invoice = createDraftInvoice(null);
        LocalDate newDueDate = invoice.getDueDate().plusDays(15);

        navigateTo("/invoices/" + invoice.getInvoiceNumber() + "/edit");
        waitForPageLoad();
        page.locator("#amount").fill("3250000");
        page.locator("#dueDate").fill(newDueDate.toString());
        page.locator("#notes").fill("Catatan diperbarui");
        page.click("#btn-simpan");
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/invoices/" + invoice.getInvoiceNumber());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Invoice berhasil diperbarui");
        assertThat(detail()).containsText("Catatan diperbarui");
        assertThat(detail()).containsText("Rp 3,250,000");

        Invoice updated = invoiceRepository.findByInvoiceNumber(invoice.getInvoiceNumber()).orElseThrow();
        assertEquals(0, new BigDecimal("3250000").compareTo(updated.getAmount()));
        assertEquals(newDueDate, updated.getDueDate());
        assertEquals("Catatan diperbarui", updated.getNotes());
        assertEquals(client.getId(), updated.getClient().getId());
        assertEquals(InvoiceStatus.DRAFT, updated.getStatus());
    }

    // ==================== Invoice Detail ====================

    @Test
    @DisplayName("Should display invoice detail with data and draft actions")
    void shouldDisplayInvoiceDetailPage() {
        Project project = seededProject();
        Invoice invoice = createDraftInvoice(project);
        String number = invoice.getInvoiceNumber();

        navigateTo("/invoices/" + number);
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Detail Invoice");
        assertThat(detail().locator("h2")).hasText(number);
        assertThat(detail().locator("a[href='/clients/" + client.getCode() + "']")).hasText(client.getName());
        assertThat(detail().locator("a[href='/projects/" + PROJECT_CODE + "']")).hasText(project.getName());
        assertThat(statusBadge()).hasText("Draf");
        assertThat(detail()).containsText("Rp 2,500,000");
        assertThat(detail()).containsText(invoice.getNotes());
        assertThat(detail().locator("a[href='/invoices/" + number + "/print']")).hasText("Cetak");
        assertThat(detail().locator("a[href='/invoices/" + number + "/edit']")).hasText("Edit");
        assertThat(page.locator("#form-send")).hasAttribute("action", "/invoices/" + number + "/send");
        assertThat(page.locator("#form-cancel")).hasAttribute("action", "/invoices/" + number + "/cancel");
        assertThat(page.locator("#form-delete")).hasAttribute("action", "/invoices/" + number + "/delete");
        assertThat(detail().locator("a[href='/invoices/" + number + "/pay']")).hasCount(0);
        assertThat(page.locator("[data-testid='payment-form-section']")).hasCount(0);
    }

    // ==================== Invoice Actions ====================

    @Test
    @DisplayName("Should send draft invoice and create revenue recognition draft transaction")
    void shouldSendDraftInvoice() {
        Invoice invoice = createDraftInvoiceWithProductLine();

        navigateTo("/invoices/" + invoice.getInvoiceNumber());
        waitForPageLoad();
        page.onDialog(dialog -> dialog.accept());
        page.locator("#form-send button[type='submit']").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/invoices/" + invoice.getInvoiceNumber());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Invoice berhasil dikirim");
        assertThat(statusBadge()).hasText("Terkirim");
        assertThat(page.locator("#form-send")).hasCount(0);
        assertThat(page.locator("[data-testid='payment-form-section']")).isVisible();

        Invoice sent = invoiceRepository.findByInvoiceNumber(invoice.getInvoiceNumber()).orElseThrow();
        assertEquals(InvoiceStatus.SENT, sent.getStatus());
        assertNotNull(sent.getSentAt());

        List<Transaction> revenue = transactionRepository
                .findBySourceDocumentTypeAndSourceDocumentIdOrderByTransactionDateAsc("INVOICE", invoice.getId());
        assertEquals(1, revenue.size());
        assertEquals(TransactionStatus.DRAFT, revenue.get(0).getStatus());
        assertEquals(0, new BigDecimal("4440000").compareTo(revenue.get(0).getAmount()));
        assertTrue(revenue.get(0).getDescription().startsWith("Invoice " + invoice.getInvoiceNumber()),
                "Unexpected description: " + revenue.get(0).getDescription());
    }

    @Test
    @DisplayName("Should cancel invoice")
    void shouldCancelInvoice() {
        Invoice invoice = createDraftInvoice(null);

        navigateTo("/invoices/" + invoice.getInvoiceNumber());
        waitForPageLoad();
        page.onDialog(dialog -> dialog.accept());
        page.locator("#form-cancel button[type='submit']").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/invoices/" + invoice.getInvoiceNumber());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Invoice dibatalkan");
        assertThat(statusBadge()).hasText("Dibatalkan");
        assertThat(page.locator("#form-cancel")).hasCount(0);
        assertThat(page.locator("#form-send")).hasCount(0);
        assertThat(page.locator("#form-delete")).isVisible();
        assertEquals(InvoiceStatus.CANCELLED,
                invoiceRepository.findByInvoiceNumber(invoice.getInvoiceNumber()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("Should delete draft invoice")
    void shouldDeleteDraftInvoice() {
        Invoice invoice = createDraftInvoice(null);

        navigateTo("/invoices/" + invoice.getInvoiceNumber());
        waitForPageLoad();
        page.onDialog(dialog -> dialog.accept());
        page.locator("#form-delete button[type='submit']").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/invoices");
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Invoice berhasil dihapus");
        assertTrue(invoiceRepository.findByInvoiceNumber(invoice.getInvoiceNumber()).isEmpty(),
                "Invoice still exists after delete: " + invoice.getInvoiceNumber());
    }

    // ==================== helpers ====================

    private Locator detail() {
        return page.locator("[data-testid='invoice-detail']");
    }

    private Locator statusBadge() {
        return page.locator("[data-testid='invoice-status']");
    }

    /** Drives the clientPicker combobox; the unique client code yields exactly one result. */
    private void selectClient() {
        var input = page.locator("#clientLabel");
        input.click();
        input.fill(client.getCode());
        Locator result = page.locator("[data-testid='client-picker-result']", new Page.LocatorOptions()
                .setHasText(client.getCode()));
        assertThat(result).hasCount(1);
        result.click();
        assertThat(page.locator("input[type='hidden'][name='client.id']")).hasValue(client.getId().toString());
    }

    private Client createClient() {
        Client c = new Client();
        c.setCode("INV-CL-" + shortId());
        c.setName("PT Klien Invoice " + shortId());
        return clientService.create(c);
    }

    private Project seededProject() {
        return projectRepository.findByCode(PROJECT_CODE)
                .orElseThrow(() -> new IllegalStateException("Seeded project missing: " + PROJECT_CODE));
    }

    private Invoice createDraftInvoice(Project project) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("INV-SVC-" + shortId());
        invoice.setClient(client);
        invoice.setProject(project);
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(30));
        invoice.setAmount(new BigDecimal("2500000"));
        invoice.setNotes("Catatan " + shortId());
        return invoiceService.create(invoice);
    }

    private Invoice createDraftInvoiceWithProductLine() {
        Product product = new Product();
        product.setCode("SVC-INV-" + shortId());
        product.setName("Jasa Implementasi " + shortId());
        product.setUnit("paket");
        product.setTrackInventory(false);
        product.setSalesAccount(chartOfAccountRepository.findByAccountCode(REVENUE_ACCOUNT)
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + REVENUE_ACCOUNT)));
        product = productRepository.save(product);

        InvoiceLine line = new InvoiceLine();
        line.setProduct(product);
        line.setDescription("Implementasi modul");
        line.setQuantity(new BigDecimal("2"));
        line.setUnitPrice(new BigDecimal("2000000"));
        line.setTaxRate(new BigDecimal("11"));
        line.calculateAmounts();

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("INV-SND-" + shortId());
        invoice.setClient(client);
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(30));
        invoice.setAmount(BigDecimal.ZERO);
        return invoiceService.create(invoice, List.of(line));
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
