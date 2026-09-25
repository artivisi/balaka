package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.Client;
import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.enums.InvoiceStatus;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ClientRepository;
import com.artivisi.accountingfinance.repository.InvoiceRepository;
import com.artivisi.accountingfinance.service.ClientService;
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
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ClientController: list with keyword search and status filter (HTMX and full page), new/edit
 * form, create/update with validation, detail page with tax and invoice sections, and
 * deactivate/activate.
 *
 * Every test works on clients it creates (unique code) so it neither depends on nor changes the
 * seeded clients other test classes use.
 */
@DisplayName("Client Controller Tests")
@Import(ServiceTestDataInitializer.class)
class ClientControllerFunctionalTest extends PlaywrightTestBase {

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ClientService clientService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Should list client row with code, name link, contact, email and status")
    void shouldDisplayClientListPage() {
        Client client = createClient("Daftar " + shortId(), true);

        navigateTo("/clients");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Daftar Klien");
        assertThat(page.locator("#btn-new-client")).hasAttribute("href", "/clients/new");
        assertThat(page.locator("#filter-active")).hasValue("");

        search(client.getCode());
        assertThat(clientRows()).hasCount(1);
        Locator cells = clientRow(client).locator("td");
        assertThat(cells.nth(0)).hasText(client.getCode());
        assertThat(cells.nth(1)).hasText(client.getName());
        assertThat(cells.nth(1).locator("a")).hasAttribute("href", "/clients/" + client.getCode());
        assertThat(cells.nth(2)).hasText(client.getContactPerson());
        assertThat(cells.nth(3)).hasText(client.getEmail());
        assertThat(cells.nth(4)).hasText("Aktif");
        assertThat(cells.nth(5).locator("a:text-is('Detail')")).hasAttribute("href", "/clients/" + client.getCode());
    }

    @Test
    @DisplayName("Should filter list by active status")
    void shouldFilterClientsByStatus() {
        String token = shortId();
        Client active = createClient("Aktif " + token, true);
        Client inactive = createClient("Nonaktif " + token, false);

        navigateTo("/clients");
        waitForPageLoad();
        search(token);
        assertThat(clientRows()).hasCount(2);

        page.locator("#filter-active").selectOption("false");
        assertThat(clientRows()).hasCount(1);
        assertThat(clientRow(inactive).locator("td").nth(4)).hasText("Nonaktif");

        page.locator("#filter-active").selectOption("true");
        assertThat(clientRows()).hasCount(1);
        assertThat(clientRow(active).locator("td").nth(4)).hasText("Aktif");

        page.locator("#filter-active").selectOption("");
        assertThat(clientRows()).hasCount(2);
    }

    @Test
    @DisplayName("Should search list by name, code and contact person, case-insensitive")
    void shouldSearchClientsByKeyword() {
        String token = shortId();
        Client first = createClient("Alpha " + token, true);
        Client second = createClient("Beta " + token, true);

        navigateTo("/clients");
        waitForPageLoad();
        search(token.toUpperCase(Locale.ROOT));
        assertThat(clientRows()).hasCount(2);
        assertThat(clientRow(first)).hasCount(1);
        assertThat(clientRow(second)).hasCount(1);

        search(second.getCode().toLowerCase(Locale.ROOT));
        assertThat(clientRows()).hasCount(1);
        assertThat(clientRow(second)).hasCount(1);

        search(first.getContactPerson());
        assertThat(clientRows()).hasCount(1);
        assertThat(clientRow(first)).hasCount(1);

        search("no-such-client-" + token);
        assertThat(clientRows()).hasCount(1);
        assertThat(clientRows().first()).hasText("Tidak ada klien");
    }

    @Test
    @DisplayName("Should apply status and search from URL parameters on full page load")
    void shouldFilterClientsWithUrlParams() {
        String token = shortId();
        createClient("Aktif " + token, true);
        Client inactive = createClient("Nonaktif " + token, false);

        navigateTo("/clients?active=false&search=" + token);
        waitForPageLoad();

        assertThat(page.locator("#filter-active")).hasValue("false");
        assertThat(page.locator("#search-input")).hasValue(token);
        assertThat(clientRows()).hasCount(1);
        assertThat(clientRow(inactive)).hasCount(1);
    }

    @Test
    @DisplayName("Should return only the client table fragment for HTMX requests")
    void shouldHandleHtmxRequestForClientList() {
        Client client = createClient("Htmx " + shortId(), true);

        APIResponse response = page.request().get(baseUrl() + "/clients?search=" + client.getCode(),
                RequestOptions.create().setHeader("HX-Request", "true"));

        assertEquals(200, response.status());
        String body = response.text().trim();
        assertTrue(body.startsWith("<div id=\"client-table\">"), "Expected table fragment, got: " + body);
        assertFalse(body.contains("<html"), "HTMX response contains full page layout");
        assertFalse(body.contains("page-title"), "HTMX response contains page header");
        assertTrue(body.contains("data-testid=\"client-row-" + client.getCode() + "\""),
                "HTMX response missing row for " + client.getCode());
    }

    @Test
    @DisplayName("Should open empty new client form")
    void shouldDisplayNewClientForm() {
        navigateTo("/clients");
        waitForPageLoad();
        page.locator("#btn-new-client").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/clients/new", page.url());
        assertThat(page.locator("#page-title")).hasText("Klien Baru");
        assertThat(form()).hasAttribute("action", "/clients/new");
        for (String field : new String[]{"code", "name", "contactPerson", "email", "phone", "address",
                "notes", "npwp", "nitku", "nik"}) {
            assertThat(page.locator("#" + field)).hasValue("");
        }
        assertThat(page.locator("#idType")).hasValue("TIN");
        assertThat(form().locator("a:text-is('Batal')")).hasAttribute("href", "/clients");
    }

    @Test
    @DisplayName("Should create client with contact and tax identity and show it on detail page")
    void shouldCreateNewClient() {
        String code = "CC-" + shortId();
        String name = "PT Klien Baru " + code;

        navigateTo("/clients/new");
        waitForPageLoad();
        page.locator("#code").fill(code);
        page.locator("#name").fill(name);
        page.locator("#contactPerson").fill("Siti Rahma");
        page.locator("#email").fill("siti@" + code.toLowerCase(Locale.ROOT) + ".example.com");
        page.locator("#phone").fill("021-5550123");
        page.locator("#address").fill("Jl. Sudirman No. 1 Jakarta");
        page.locator("#notes").fill("Catatan " + code);
        page.locator("#npwp").fill("01.234.567.8-901.000");
        page.locator("#nitku").fill("0123456789010000000000");
        submit();

        assertEquals(baseUrl() + "/clients/" + code, page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Klien berhasil ditambahkan");

        Client saved = clientRepository.findByCode(code)
                .orElseThrow(() -> new AssertionError("Client not persisted: " + code));
        assertEquals(name, saved.getName());
        assertEquals("Siti Rahma", saved.getContactPerson());
        assertEquals("siti@" + code.toLowerCase(Locale.ROOT) + ".example.com", saved.getEmail());
        assertEquals("021-5550123", saved.getPhone());
        assertEquals("Jl. Sudirman No. 1 Jakarta", saved.getAddress());
        assertEquals("Catatan " + code, saved.getNotes());
        assertEquals("TIN", saved.getIdType());
        assertEquals("01.234.567.8-901.000", saved.getNpwp());
        assertEquals("0123456789010000000000", saved.getNitku());
        assertTrue(saved.getActive());

        Locator detail = page.locator("[data-testid='client-detail']");
        assertThat(detail.locator("h2")).hasText(name);
        assertThat(detail.locator("[data-testid='client-code']")).hasText(code);
        assertThat(detail.locator("[data-testid='client-status']")).hasText("Aktif");
        assertThat(infoValue("Kontak")).hasText("Siti Rahma");
        assertThat(infoValue("Telepon")).hasText("021-5550123");
        assertThat(infoValue("Alamat")).hasText("Jl. Sudirman No. 1 Jakarta");
        assertThat(infoValue("Catatan")).hasText("Catatan " + code);
    }

    @Test
    @DisplayName("Should reject blank name and persist nothing")
    void shouldShowValidationErrorForEmptyName() {
        String code = "CC-" + shortId();

        navigateTo("/clients/new");
        waitForPageLoad();
        page.locator("#btn-simpan").evaluate("b => b.form.noValidate = true");
        page.locator("#code").fill(code);
        submit();

        assertThat(page.locator("#page-title")).hasText("Klien Baru");
        assertThat(page.locator("#name + p.text-red-500")).hasText("Nama klien wajib diisi");
        assertThat(page.locator("#code")).hasValue(code);
        assertFalse(clientRepository.existsByCode(code), "Client persisted despite blank name");
    }

    @Test
    @DisplayName("Should reject invalid NPWP format and persist nothing")
    void shouldRejectInvalidNpwpFormat() {
        String code = "CC-" + shortId();

        navigateTo("/clients/new");
        waitForPageLoad();
        page.locator("#code").fill(code);
        page.locator("#name").fill("Klien NPWP " + code);
        page.locator("#npwp").fill("12345-invalid");
        submit();

        assertThat(page.locator("#page-title")).hasText("Klien Baru");
        assertThat(page.locator("#npwp + p.text-red-500"))
                .hasText("Format NPWP tidak valid (XX.XXX.XXX.X-XXX.XXX atau 16 digit)");
        assertThat(page.locator("#npwp")).hasValue("12345-invalid");
        assertFalse(clientRepository.existsByCode(code), "Client persisted despite invalid NPWP");
    }

    @Test
    @DisplayName("Should reject duplicate code on create and keep existing client unchanged")
    void shouldShowValidationErrorForDuplicateCode() {
        Client existing = createClient("Asli " + shortId(), true);
        String name = "Duplikat " + shortId();

        navigateTo("/clients/new");
        waitForPageLoad();
        page.locator("#code").fill(existing.getCode());
        page.locator("#name").fill(name);
        submit();

        assertThat(page.locator("#page-title")).hasText("Klien Baru");
        assertThat(page.locator("#code + p.text-red-500"))
                .hasText("Client code already exists: " + existing.getCode());
        assertThat(page.locator("#name")).hasValue(name);
        assertEquals(existing.getName(), clientRepository.findByCode(existing.getCode()).orElseThrow().getName());
        assertFalse(clientRepository.findAll().stream().anyMatch(c -> name.equals(c.getName())),
                "Client persisted despite duplicate code");
    }

    @Test
    @DisplayName("Should show client details, tax identity, empty projects/invoices and actions")
    void shouldDisplayClientDetailPage() {
        Client client = createClient("Detail " + shortId(), true);

        navigateTo("/clients");
        waitForPageLoad();
        search(client.getCode());
        clientRow(client).locator("a:text-is('Detail')").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/clients/" + client.getCode(), page.url());
        assertThat(page.locator("#page-title")).hasText("Detail Klien");
        Locator detail = page.locator("[data-testid='client-detail']");
        assertThat(detail.locator("h2").first()).hasText(client.getName());
        assertThat(detail.locator("[data-testid='client-code']")).hasText(client.getCode());
        assertThat(detail.locator("[data-testid='client-status']")).hasText("Aktif");
        assertThat(infoValue("Kontak")).hasText(client.getContactPerson());
        assertThat(infoValue("Email")).hasText(client.getEmail());
        assertThat(infoValue("Telepon")).hasText(client.getPhone());
        assertThat(infoValue("Tipe Identitas")).hasText("NPWP (PKP)");
        assertThat(infoValue("NPWP")).hasText(client.getNpwp());
        assertThat(detail).containsText("Belum ada proyek");
        assertThat(page.locator("[data-testid='invoices-section']")).containsText("Belum ada invoice");
        assertThat(page.locator("[data-testid='tax-summary-section']")).containsText("Belum ada transaksi pajak");
        assertThat(detail.locator("a:text-is('Edit')")).hasAttribute("href", "/clients/" + client.getCode() + "/edit");
        assertThat(page.locator("[data-testid='view-statement']"))
                .hasAttribute("href", "/statements/client/" + client.getCode());
        assertThat(page.locator("#form-deactivate")).hasCount(1);
        assertThat(page.locator("form[action$='/activate']")).hasCount(0);
    }

    @Test
    @DisplayName("Should list the client's invoices with number link, total and status on detail page")
    void shouldDisplayInvoicesSectionOnClientDetail() {
        Client client = createClient("Invoice " + shortId(), true);
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("INV-CC-" + shortId());
        invoice.setClient(client);
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(30));
        invoice.setAmount(new BigDecimal("10000000"));
        invoice.setTaxAmount(new BigDecimal("1100000"));
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoiceRepository.save(invoice);

        navigateTo("/clients/" + client.getCode());
        waitForPageLoad();

        Locator rows = page.locator("[data-testid='invoices-section'] tbody tr");
        assertThat(rows).hasCount(1);
        Locator cells = rows.first().locator("td");
        assertThat(cells.nth(0).locator("a")).hasText(invoice.getInvoiceNumber());
        assertThat(cells.nth(0).locator("a")).hasAttribute("href", "/invoices/" + invoice.getInvoiceNumber());
        assertThat(cells.nth(3)).hasText("11,100,000");
        assertThat(cells.nth(4)).hasText("Draf");
    }

    @Test
    @DisplayName("Should open edit form prefilled with all client fields")
    void shouldDisplayClientEditForm() {
        Client client = createClient("Edit " + shortId(), true);

        navigateTo("/clients/" + client.getCode());
        waitForPageLoad();
        page.locator("[data-testid='client-detail'] a:text-is('Edit')").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/clients/" + client.getCode() + "/edit", page.url());
        assertThat(page.locator("#page-title")).hasText("Edit Klien");
        assertThat(form()).hasAttribute("action", "/clients/" + client.getCode());
        assertThat(page.locator("#code")).hasValue(client.getCode());
        assertThat(page.locator("#name")).hasValue(client.getName());
        assertThat(page.locator("#contactPerson")).hasValue(client.getContactPerson());
        assertThat(page.locator("#email")).hasValue(client.getEmail());
        assertThat(page.locator("#phone")).hasValue(client.getPhone());
        assertThat(page.locator("#address")).hasValue(client.getAddress());
        assertThat(page.locator("#idType")).hasValue("TIN");
        assertThat(page.locator("#npwp")).hasValue(client.getNpwp());
        assertThat(form().locator("a:text-is('Batal')")).hasAttribute("href", "/clients/" + client.getCode());
    }

    @Test
    @DisplayName("Should update client fields and code, redirecting to the new code")
    void shouldUpdateClient() {
        Client client = createClient("Ubah " + shortId(), true);
        String newCode = "CC-" + shortId();
        String newName = "PT Diperbarui " + newCode;

        navigateTo("/clients/" + client.getCode() + "/edit");
        waitForPageLoad();
        page.locator("#code").fill(newCode);
        page.locator("#name").fill(newName);
        page.locator("#email").fill("baru@" + newCode.toLowerCase(Locale.ROOT) + ".example.com");
        page.locator("#npwp").fill("1234567890123456");
        submit();

        assertEquals(baseUrl() + "/clients/" + newCode, page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Klien berhasil diperbarui");
        assertThat(page.locator("[data-testid='client-detail'] h2").first()).hasText(newName);

        Client updated = clientRepository.findByCode(newCode)
                .orElseThrow(() -> new AssertionError("Client not found under new code " + newCode));
        assertEquals(client.getId(), updated.getId());
        assertEquals(newName, updated.getName());
        assertEquals("baru@" + newCode.toLowerCase(Locale.ROOT) + ".example.com", updated.getEmail());
        assertEquals("1234567890123456", updated.getNpwp());
        assertEquals(client.getContactPerson(), updated.getContactPerson());
        assertFalse(clientRepository.existsByCode(client.getCode()), "Old code still present");
    }

    @Test
    @DisplayName("Should reject update to another client's code and keep the form bound to the edited client")
    void shouldRejectUpdateToDuplicateCode() {
        Client client = createClient("Ubah " + shortId(), true);
        Client other = createClient("Lain " + shortId(), true);
        String fixedCode = "CC-" + shortId();

        navigateTo("/clients/" + client.getCode() + "/edit");
        waitForPageLoad();
        page.locator("#code").fill(other.getCode());
        submit();

        assertThat(page.locator("#page-title")).hasText("Edit Klien");
        assertThat(page.locator("#code + p.text-red-500")).hasText("Client code already exists: " + other.getCode());
        assertThat(page.locator("#code")).hasValue(other.getCode());
        assertThat(form()).hasAttribute("action", "/clients/" + client.getCode());
        assertThat(form().locator("a:text-is('Batal')")).hasAttribute("href", "/clients/" + client.getCode());

        page.locator("#code").fill(fixedCode);
        submit();

        assertEquals(baseUrl() + "/clients/" + fixedCode, page.url());
        assertEquals(client.getId(), clientRepository.findByCode(fixedCode).orElseThrow().getId());
        Client untouched = clientRepository.findByCode(other.getCode()).orElseThrow();
        assertEquals(other.getId(), untouched.getId());
        assertEquals(other.getName(), untouched.getName());
    }

    @Test
    @DisplayName("Should keep client active when deactivate confirmation is dismissed")
    void shouldKeepClientActiveWhenDeactivateDismissed() {
        Client client = createClient("Batal Nonaktif " + shortId(), true);

        navigateTo("/clients/" + client.getCode());
        waitForPageLoad();
        page.onceDialog(dialog -> {
            assertEquals("Nonaktifkan klien ini?", dialog.message());
            dialog.dismiss();
        });
        page.locator("#form-deactivate button[type='submit']").click();

        assertThat(page.locator("[data-testid='client-status']")).hasText("Aktif");
        assertEquals(baseUrl() + "/clients/" + client.getCode(), page.url());
        assertTrue(clientRepository.findByCode(client.getCode()).orElseThrow().getActive());
    }

    @Test
    @DisplayName("Should deactivate client after confirmation")
    void shouldDeactivateClient() {
        Client client = createClient("Nonaktif " + shortId(), true);

        navigateTo("/clients/" + client.getCode());
        waitForPageLoad();
        page.onceDialog(dialog -> dialog.accept());
        page.locator("#form-deactivate button[type='submit']").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/clients/" + client.getCode(), page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Klien berhasil dinonaktifkan");
        assertThat(page.locator("[data-testid='client-status']")).hasText("Nonaktif");
        assertThat(page.locator("#form-deactivate")).hasCount(0);
        assertThat(page.locator("form[action$='/activate']")).hasCount(1);
        assertFalse(clientRepository.findByCode(client.getCode()).orElseThrow().getActive());
    }

    @Test
    @DisplayName("Should activate inactive client")
    void shouldActivateClient() {
        Client client = createClient("Aktifkan " + shortId(), false);

        navigateTo("/clients/" + client.getCode());
        waitForPageLoad();
        assertThat(page.locator("[data-testid='client-status']")).hasText("Nonaktif");
        page.locator("form[action$='/activate'] button[type='submit']").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/clients/" + client.getCode(), page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Klien berhasil diaktifkan");
        assertThat(page.locator("[data-testid='client-status']")).hasText("Aktif");
        assertThat(page.locator("#form-deactivate")).hasCount(1);
        assertTrue(clientRepository.findByCode(client.getCode()).orElseThrow().getActive());
    }

    // ==================== helpers ====================

    private Client createClient(String name, boolean active) {
        String id = shortId();
        Client c = new Client();
        c.setCode("CC-" + id);
        c.setName(name);
        c.setContactPerson("Kontak " + id);
        c.setEmail("kontak-" + id + "@example.com");
        c.setPhone("021-" + id.substring(0, 6));
        c.setAddress("Jl. Uji " + id);
        c.setNpwp(uniqueNpwp());
        Client saved = clientService.create(c);
        if (!active) {
            clientService.deactivate(saved.getId());
            saved.setActive(false);
        }
        return saved;
    }

    private void search(String keyword) {
        Locator input = page.locator("#search-input");
        input.fill(keyword);
        input.press("End");
    }

    private Locator clientRows() {
        return page.locator("[data-testid='client-table'] tbody tr");
    }

    private Locator clientRow(Client client) {
        return page.locator("[data-testid='client-row-" + client.getCode() + "']");
    }

    private Locator infoValue(String label) {
        return page.locator("[data-testid='client-detail'] div:has(> p.text-gray-500:text-is('" + label + "')) > p.font-medium");
    }

    private Locator form() {
        return page.locator("form:has(#btn-simpan)");
    }

    private void submit() {
        page.locator("#btn-simpan").click();
        waitForPageLoad();
    }

    private static String uniqueNpwp() {
        String d = String.format("%015d", ThreadLocalRandom.current().nextLong(1_000_000_000_000_000L));
        return d.substring(0, 2) + "." + d.substring(2, 5) + "." + d.substring(5, 8) + "." + d.charAt(8)
                + "-" + d.substring(9, 12) + "." + d.substring(12, 15);
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
