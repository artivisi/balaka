package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.Vendor;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.VendorRepository;
import com.artivisi.accountingfinance.service.VendorService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VendorController: list with keyword search and status filter, new/edit form, create/update with
 * validation, detail page, deactivate/activate and the vendor picker search endpoint.
 *
 * Every test works on vendors it creates (unique code); seeded data is only read (expense account 5.1.20).
 */
@DisplayName("Vendor Controller Tests")
@Import(ServiceTestDataInitializer.class)
class VendorControllerFunctionalTest extends PlaywrightTestBase {

    private static final String EXPENSE_ACCOUNT = "5.1.20";

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private VendorService vendorService;

    @Autowired
    private ChartOfAccountRepository chartOfAccountRepository;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Should list vendor row with code link, name, contact, phone and status")
    void shouldListVendorRow() {
        Vendor vendor = createVendor("Daftar " + shortId(), true);

        navigateTo("/vendors");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Daftar Vendor");
        assertThat(page.locator("#btn-new-vendor")).hasAttribute("href", "/vendors/new");

        search(vendor.getCode());
        assertThat(page).hasURL(baseUrl() + "/vendors?search=" + vendor.getCode() + "&active=");
        assertThat(vendorRows()).hasCount(1);
        Locator cells = vendorRow(vendor).locator("td");
        assertThat(cells.nth(0)).hasText(vendor.getCode());
        assertThat(cells.nth(0).locator("a")).hasAttribute("href", "/vendors/" + vendor.getCode());
        assertThat(cells.nth(1)).hasText(vendor.getName());
        assertThat(cells.nth(2)).hasText(vendor.getContactPerson());
        assertThat(cells.nth(3)).hasText(vendor.getPhone());
        assertThat(cells.nth(4)).hasText("Aktif");
        assertThat(cells.nth(5).locator("a:text-is('Detail')")).hasAttribute("href", "/vendors/" + vendor.getCode());
    }

    @Test
    @DisplayName("Should search by code, name and contact person case-insensitively; no match shows empty row")
    void shouldSearchVendors() {
        String token = shortId();
        Vendor byName = createVendor("Cari " + token, true);
        Vendor other = createVendor("Lain " + shortId(), true);

        navigateTo("/vendors");
        waitForPageLoad();
        search(("cari " + token).toUpperCase());
        assertThat(page.locator("#search-vendor")).hasValue(("cari " + token).toUpperCase());
        assertThat(vendorRows()).hasCount(1);
        assertThat(vendorRow(byName)).hasCount(1);

        search(other.getCode().toLowerCase());
        assertThat(vendorRows()).hasCount(1);
        assertThat(vendorRow(other)).hasCount(1);

        search(byName.getContactPerson());
        assertThat(vendorRows()).hasCount(1);
        assertThat(vendorRow(byName)).hasCount(1);

        search("tidak-ada-" + shortId());
        assertThat(vendorRows()).hasCount(0);
        assertThat(page.locator("[data-testid='vendor-table'] tbody")).hasText("Belum ada vendor");
    }

    @Test
    @DisplayName("Should filter by status: active only, inactive only, all")
    void shouldFilterVendorsByStatus() {
        String token = shortId();
        Vendor active = createVendor("Status " + token + " A", true);
        Vendor inactive = createVendor("Status " + token + " B", false);

        navigateTo("/vendors");
        waitForPageLoad();
        page.locator("#search-vendor").fill("Status " + token);

        page.locator("#filter-active").selectOption("true");
        submitFilter();
        assertThat(page.locator("#filter-active")).hasValue("true");
        assertThat(vendorRows()).hasCount(1);
        assertThat(vendorRow(active).locator("td").nth(4)).hasText("Aktif");

        page.locator("#filter-active").selectOption("false");
        submitFilter();
        assertThat(page.locator("#filter-active")).hasValue("false");
        assertThat(vendorRows()).hasCount(1);
        assertThat(vendorRow(inactive).locator("td").nth(4)).hasText("Nonaktif");

        page.locator("#filter-active").selectOption("");
        submitFilter();
        assertThat(vendorRows()).hasCount(2);
        assertEquals(List.of(active.getCode(), inactive.getCode()),
                vendorRows().locator("td:first-child").allTextContents().stream().map(String::strip).toList());
    }

    @Test
    @DisplayName("Should open empty new vendor form with expense account options")
    void shouldDisplayNewVendorForm() {
        navigateTo("/vendors");
        waitForPageLoad();
        page.locator("#btn-new-vendor").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/vendors/new", page.url());
        assertThat(page.locator("#page-title")).hasText("Vendor Baru");
        assertThat(form()).hasAttribute("action", "/vendors/new");
        for (String field : List.of("#code", "#name", "#contactPerson", "#email", "#phone", "#paymentTermDays",
                "#address", "#npwp", "#nitku", "#bankName", "#bankAccountNumber", "#bankAccountName", "#notes")) {
            assertThat(page.locator(field)).hasValue("");
        }
        assertThat(page.locator("#defaultExpenseAccount")).hasValue("");
        ChartOfAccount expense = account(EXPENSE_ACCOUNT);
        assertThat(page.locator("#defaultExpenseAccount option[value='" + expense.getId() + "']"))
                .hasText(EXPENSE_ACCOUNT + " - Beban Cloud & Server");
        assertThat(form().locator("a:text-is('Batal')")).hasAttribute("href", "/vendors");
    }

    @Test
    @DisplayName("Should create vendor with all fields and show them on the detail page")
    void shouldCreateVendorWithAllFields() {
        String code = "VND-" + shortId();
        String name = "PT Pemasok " + code;
        ChartOfAccount expense = account(EXPENSE_ACCOUNT);

        navigateTo("/vendors/new");
        waitForPageLoad();
        page.locator("#code").fill(code);
        page.locator("#name").fill(name);
        page.locator("#contactPerson").fill("Andi Wijaya");
        page.locator("#email").fill("andi@pemasok.example.com");
        page.locator("#phone").fill("021-5550123");
        page.locator("#paymentTermDays").fill("45");
        page.locator("#address").fill("Jl. Sudirman No. 1 Jakarta");
        page.locator("#defaultExpenseAccount").selectOption(expense.getId().toString());
        page.locator("#npwp").fill("01.234.567.8-901.000");
        page.locator("#nitku").fill("0123456789012345000000");
        page.locator("#bankName").fill("Bank BCA");
        page.locator("#bankAccountNumber").fill("1234567890");
        page.locator("#bankAccountName").fill(name);
        page.locator("#notes").fill("Pemasok server");
        submit();

        assertEquals(baseUrl() + "/vendors/" + code, page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Vendor berhasil dibuat");

        Vendor saved = vendorRepository.findByCode(code)
                .orElseThrow(() -> new AssertionError("Vendor not persisted: " + code));
        assertEquals(name, saved.getName());
        assertEquals("Andi Wijaya", saved.getContactPerson());
        assertEquals("andi@pemasok.example.com", saved.getEmail());
        assertEquals("021-5550123", saved.getPhone());
        assertEquals(45, saved.getPaymentTermDays());
        assertEquals("Jl. Sudirman No. 1 Jakarta", saved.getAddress());
        assertEquals("01.234.567.8-901.000", saved.getNpwp());
        assertEquals("0123456789012345000000", saved.getNitku());
        assertEquals("Bank BCA", saved.getBankName());
        assertEquals("1234567890", saved.getBankAccountNumber());
        assertEquals(name, saved.getBankAccountName());
        assertEquals("Pemasok server", saved.getNotes());
        assertTrue(saved.getActive());
        assertEquals(expense.getId(), vendorService.findByCode(code).getDefaultExpenseAccount().getId());

        Locator detail = page.locator("[data-testid='vendor-detail']");
        assertThat(detail.locator("h2")).hasText(name);
        assertThat(page.locator("[data-testid='vendor-status']")).hasText("Aktif");
        assertThat(infoValue("Kontak")).hasText("Andi Wijaya");
        assertThat(infoValue("Email")).hasText("andi@pemasok.example.com");
        assertThat(infoValue("Telepon")).hasText("021-5550123");
        assertThat(infoValue("Termin Pembayaran")).hasText("45 hari");
        assertThat(infoValue("Akun Beban Default")).hasText(EXPENSE_ACCOUNT + " - Beban Cloud & Server");
        assertThat(infoValue("Alamat")).hasText("Jl. Sudirman No. 1 Jakarta");
        assertThat(infoValue("Catatan")).hasText("Pemasok server");
        assertThat(infoValue("NPWP")).hasText("01.234.567.8-901.000");
        assertThat(infoValue("NITKU")).hasText("0123456789012345000000");
        assertThat(infoValue("Bank")).hasText("Bank BCA");
        assertThat(infoValue("Nomor Rekening")).hasText("1234567890");
        assertThat(infoValue("Atas Nama")).hasText(name);
        assertThat(page.locator("[data-testid='view-statement']")).hasAttribute("href", "/statements/vendor/" + code);
        assertThat(detail.locator("a:text-is('Edit')")).hasAttribute("href", "/vendors/" + code + "/edit");
        assertThat(page.locator("#form-deactivate")).hasCount(1);
        assertThat(page.locator("#form-activate")).hasCount(0);
    }

    @Test
    @DisplayName("Should reject blank code and name, invalid email; persist nothing")
    void shouldRejectInvalidVendor() {
        long before = vendorRepository.count();
        String name = "Tanpa Kode " + shortId();

        navigateTo("/vendors/new");
        waitForPageLoad();
        page.locator("#code").evaluate("el => el.removeAttribute('required')");
        page.locator("#name").evaluate("el => el.removeAttribute('required')");
        page.locator("#email").evaluate("el => el.type = 'text'");
        page.locator("#email").fill("bukan-email");
        submit();

        assertEquals(baseUrl() + "/vendors/new", page.url());
        assertThat(page.locator("#page-title")).hasText("Vendor Baru");
        assertThat(page.locator("#code + p.text-red-500")).hasText("Kode vendor wajib diisi");
        assertThat(page.locator("#name + p.text-red-500")).hasText("Nama vendor wajib diisi");
        assertThat(page.locator("form ul li:text-is('Format email tidak valid')")).hasCount(1);
        assertEquals(before, vendorRepository.count());

        page.locator("#code").evaluate("el => el.removeAttribute('required')");
        page.locator("#name").fill(name);
        page.locator("#email").fill("");
        submit();
        assertThat(page.locator("#code + p.text-red-500")).hasText("Kode vendor wajib diisi");
        assertThat(page.locator("#name")).hasValue(name);
        assertEquals(before, vendorRepository.count());
    }

    @Test
    @DisplayName("Should reject duplicate code on create and keep the existing vendor unchanged")
    void shouldRejectDuplicateCodeOnCreate() {
        Vendor existing = createVendor("Asli " + shortId(), true);

        navigateTo("/vendors/new");
        waitForPageLoad();
        page.locator("#code").fill(existing.getCode());
        page.locator("#name").fill("Penyusup " + shortId());
        submit();

        assertThat(page.locator("#page-title")).hasText("Vendor Baru");
        assertThat(page.locator("#code + p.text-red-500")).hasText("Kode vendor sudah digunakan: " + existing.getCode());
        assertThat(page.locator("#code")).hasValue(existing.getCode());
        Vendor unchanged = vendorRepository.findByCode(existing.getCode()).orElseThrow();
        assertEquals(existing.getId(), unchanged.getId());
        assertEquals(existing.getName(), unchanged.getName());
    }

    @Test
    @DisplayName("Should open edit form prefilled from the detail page")
    void shouldDisplayVendorEditForm() {
        Vendor vendor = createVendor("Edit " + shortId(), true);
        ChartOfAccount expense = account(EXPENSE_ACCOUNT);

        navigateTo("/vendors/" + vendor.getCode());
        waitForPageLoad();
        page.locator("[data-testid='vendor-detail'] a:text-is('Edit')").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/vendors/" + vendor.getCode() + "/edit", page.url());
        assertThat(page.locator("#page-title")).hasText("Edit Vendor");
        assertThat(form()).hasAttribute("action", "/vendors/" + vendor.getCode());
        assertThat(page.locator("#code")).hasValue(vendor.getCode());
        assertThat(page.locator("#name")).hasValue(vendor.getName());
        assertThat(page.locator("#contactPerson")).hasValue(vendor.getContactPerson());
        assertThat(page.locator("#phone")).hasValue(vendor.getPhone());
        assertThat(page.locator("#paymentTermDays")).hasValue("30");
        assertThat(page.locator("#defaultExpenseAccount")).hasValue(expense.getId().toString());
        assertThat(form().locator("a:text-is('Batal')")).hasAttribute("href", "/vendors/" + vendor.getCode());
    }

    @Test
    @DisplayName("Should update vendor fields and code, redirecting to the new code; clearing the expense account")
    void shouldUpdateVendor() {
        Vendor vendor = createVendor("Ubah " + shortId(), true);
        String newCode = "VND-" + shortId();
        String newName = "PT Diperbarui " + newCode;

        navigateTo("/vendors/" + vendor.getCode() + "/edit");
        waitForPageLoad();
        page.locator("#code").fill(newCode);
        page.locator("#name").fill(newName);
        page.locator("#paymentTermDays").fill("14");
        page.locator("#bankName").fill("Bank Mandiri");
        page.locator("#defaultExpenseAccount").selectOption("");
        submit();

        assertEquals(baseUrl() + "/vendors/" + newCode, page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Vendor berhasil diperbarui");
        assertThat(page.locator("[data-testid='vendor-detail'] h2")).hasText(newName);
        assertThat(infoValue("Termin Pembayaran")).hasText("14 hari");
        assertThat(infoValue("Bank")).hasText("Bank Mandiri");

        Vendor updated = vendorService.findByCode(newCode);
        assertEquals(vendor.getId(), updated.getId());
        assertEquals(newName, updated.getName());
        assertEquals(14, updated.getPaymentTermDays());
        assertEquals("Bank Mandiri", updated.getBankName());
        assertEquals(vendor.getContactPerson(), updated.getContactPerson());
        assertNull(updated.getDefaultExpenseAccount());
        assertFalse(vendorRepository.existsByCode(vendor.getCode()), "Old code still present");
    }

    @Test
    @DisplayName("Should reject update to another vendor's code; form stays bound to the edited vendor")
    void shouldRejectUpdateToDuplicateCode() {
        Vendor vendor = createVendor("Ubah " + shortId(), true);
        Vendor other = createVendor("Lain " + shortId(), true);
        String fixedCode = "VND-" + shortId();

        navigateTo("/vendors/" + vendor.getCode() + "/edit");
        waitForPageLoad();
        page.locator("#code").fill(other.getCode());
        page.locator("#name").fill("Nama Baru " + fixedCode);
        submit();

        assertThat(page.locator("#page-title")).hasText("Edit Vendor");
        assertThat(page.locator("#code + p.text-red-500")).hasText("Kode vendor sudah digunakan: " + other.getCode());
        assertThat(page.locator("#code")).hasValue(other.getCode());
        assertThat(form()).hasAttribute("action", "/vendors/" + vendor.getCode());
        assertThat(form().locator("a:text-is('Batal')")).hasAttribute("href", "/vendors/" + vendor.getCode());
        assertEquals(other.getName(), vendorRepository.findByCode(other.getCode()).orElseThrow().getName());
        assertEquals(vendor.getName(), vendorRepository.findByCode(vendor.getCode()).orElseThrow().getName());

        page.locator("#code").fill(fixedCode);
        submit();

        assertEquals(baseUrl() + "/vendors/" + fixedCode, page.url());
        Vendor updated = vendorRepository.findByCode(fixedCode).orElseThrow();
        assertEquals(vendor.getId(), updated.getId());
        assertEquals("Nama Baru " + fixedCode, updated.getName());
        Vendor untouched = vendorRepository.findById(other.getId()).orElseThrow();
        assertEquals(other.getCode(), untouched.getCode());
        assertEquals(other.getName(), untouched.getName());
    }

    @Test
    @DisplayName("Should keep vendor active when the deactivate confirmation is dismissed")
    void shouldKeepVendorActiveWhenConfirmationDismissed() {
        Vendor vendor = createVendor("Batal " + shortId(), true);

        navigateTo("/vendors/" + vendor.getCode());
        waitForPageLoad();
        List<String> dialogs = new ArrayList<>();
        page.onceDialog(dialog -> {
            dialogs.add(dialog.message());
            dialog.dismiss();
        });
        page.locator("#form-deactivate button").click();
        waitForPageLoad();

        assertEquals(List.of("Nonaktifkan vendor ini?"), dialogs);
        assertEquals(baseUrl() + "/vendors/" + vendor.getCode(), page.url());
        assertThat(page.locator("[data-testid='vendor-status']")).hasText("Aktif");
        assertTrue(vendorRepository.findById(vendor.getId()).orElseThrow().getActive());
    }

    @Test
    @DisplayName("Should deactivate after confirmation, then activate again")
    void shouldDeactivateAndActivateVendor() {
        Vendor vendor = createVendor("Status " + shortId(), true);

        navigateTo("/vendors/" + vendor.getCode());
        waitForPageLoad();
        page.onceDialog(dialog -> dialog.accept());
        page.locator("#form-deactivate button").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/vendors/" + vendor.getCode(), page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Vendor berhasil dinonaktifkan");
        assertThat(page.locator("[data-testid='vendor-status']")).hasText("Nonaktif");
        assertThat(page.locator("#form-deactivate")).hasCount(0);
        assertFalse(vendorRepository.findById(vendor.getId()).orElseThrow().getActive());

        page.locator("#form-activate button").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/vendors/" + vendor.getCode(), page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Vendor berhasil diaktifkan");
        assertThat(page.locator("[data-testid='vendor-status']")).hasText("Aktif");
        assertThat(page.locator("#form-activate")).hasCount(0);
        assertTrue(vendorRepository.findById(vendor.getId()).orElseThrow().getActive());
    }

    @Test
    @DisplayName("Vendor picker search returns matching active vendors only")
    void shouldSearchActiveVendorsForPicker() throws Exception {
        String token = shortId();
        Vendor active = createVendor("Picker " + token + " A", true);
        createVendor("Picker " + token + " B", false);

        APIResponse response = page.request().get(baseUrl() + "/vendors/search?q=picker%20" + token);
        assertEquals(200, response.status());
        JsonNode results = new ObjectMapper().readTree(response.text());
        assertEquals(1, results.size());
        assertEquals(active.getId().toString(), results.get(0).get("id").asText());
        assertEquals(active.getCode(), results.get(0).get("code").asText());
        assertEquals(active.getName(), results.get(0).get("name").asText());
    }

    private Vendor createVendor(String name, boolean active) {
        Vendor vendor = new Vendor();
        vendor.setCode("VND-" + shortId());
        vendor.setName(name);
        vendor.setContactPerson("Kontak " + shortId());
        vendor.setPhone("0812" + ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        vendor.setPaymentTermDays(30);
        ChartOfAccount expense = new ChartOfAccount();
        expense.setId(account(EXPENSE_ACCOUNT).getId());
        vendor.setDefaultExpenseAccount(expense);
        Vendor saved = vendorService.create(vendor);
        if (!active) {
            vendorService.deactivate(saved.getId());
        }
        return saved;
    }

    private ChartOfAccount account(String code) {
        return chartOfAccountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + code));
    }

    private void search(String keyword) {
        page.locator("#search-vendor").fill(keyword);
        submitFilter();
    }

    private void submitFilter() {
        page.locator("[data-testid='vendor-list'] form button:text-is('Filter')").click();
        waitForPageLoad();
    }

    private Locator vendorRows() {
        return page.locator("[data-testid='vendor-table'] tbody tr:has(td a)");
    }

    private Locator vendorRow(Vendor vendor) {
        return page.locator("[data-testid='vendor-table'] tbody tr:has(td:first-child a:text-is('" + vendor.getCode() + "'))");
    }

    private Locator infoValue(String label) {
        return page.locator("[data-testid='vendor-detail'] div:has(> p.text-gray-500:text-is('" + label + "')) > p.font-medium");
    }

    private Locator form() {
        return page.locator("form:has(#btn-simpan)");
    }

    private void submit() {
        page.locator("#btn-simpan").click();
        waitForPageLoad();
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }
}
