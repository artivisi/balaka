package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.SalaryComponent;
import com.artivisi.accountingfinance.entity.SalaryComponentType;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.SalaryComponentRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.service.SalaryComponentService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SalaryComponentController: list with search, type and active filters, new/edit form with the
 * fixed/percentage toggle, create/update with validation, detail page, deactivate/activate,
 * system-component protection and the SALARY_COMPONENT_MANAGE permission.
 *
 * Seeded components feed payroll calculations asserted with exact amounts elsewhere, so they are
 * only read here; every change is made on components this class creates (unique code).
 */
@DisplayName("Salary Component Controller Tests")
@Import(ServiceTestDataInitializer.class)
class SalaryComponentControllerFunctionalTest extends PlaywrightTestBase {

    @Autowired
    private SalaryComponentRepository componentRepository;

    @Autowired
    private SalaryComponentService componentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Should list seeded components with code, name, description, type, default value and status")
    void shouldDisplaySalaryComponentListPage() {
        loginAsAdmin();
        navigateTo("/salary-components");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Komponen Gaji");
        assertThat(page.locator("#btn-new-component")).hasAttribute("href", "/salary-components/new");
        assertThat(page.locator("#filter-type")).hasValue("");
        assertThat(page.locator("#filter-active")).hasValue("");

        SalaryComponent gapok = seeded("GAPOK");
        search("GAPOK");
        assertThat(componentRow("GAPOK")).hasCount(1);
        Locator cells = componentRow("GAPOK").locator("td");
        assertThat(cells.nth(1).locator("div.font-medium")).hasText("Gaji Pokok");
        assertThat(cells.nth(1).locator("div.text-gray-500")).hasText("Gaji pokok bulanan karyawan");
        assertThat(cells.nth(2)).hasText("Pendapatan");
        assertThat(cells.nth(3)).hasText("-");
        assertThat(cells.nth(4)).hasText("Aktif");
        assertThat(cells.nth(5).locator("a:text-is('Lihat')")).hasAttribute("href", "/salary-components/" + gapok.getId());

        search("TJ-TRANS");
        assertThat(componentRow("TJ-TRANS")).hasCount(1);
        assertThat(componentRow("TJ-TRANS").locator("td").nth(3)).hasText("Rp 500,000");

        search("BPJS-KES-P");
        assertThat(componentRow("BPJS-KES-P")).hasCount(1);
        assertThat(componentRow("BPJS-KES-P").locator("td").nth(2)).hasText("Kontribusi Perusahaan");
        assertThat(componentRow("BPJS-KES-P").locator("td").nth(3)).hasText("4.00%");

        search("BPJS-JHT-K");
        assertThat(componentRow("BPJS-JHT-K")).hasCount(1);
        assertThat(componentRow("BPJS-JHT-K").locator("td").nth(2)).hasText("Potongan");
        assertThat(componentRow("BPJS-JHT-K").locator("td").nth(3)).hasText("2.00%");
    }

    @Test
    @DisplayName("Should filter list by component type and active flag via HTMX selects")
    void shouldFilterComponentsByTypeAndStatus() {
        loginAsAdmin();
        String token = shortId();
        SalaryComponent earning = createComponent("Pendapatan " + token, SalaryComponentType.EARNING, true);
        SalaryComponent deduction = createComponent("Potongan " + token, SalaryComponentType.DEDUCTION, true);
        SalaryComponent contribution = createComponent("Kontribusi " + token,
                SalaryComponentType.COMPANY_CONTRIBUTION, false);

        navigateTo("/salary-components");
        waitForPageLoad();
        search(token);
        assertThat(componentRows()).hasCount(3);

        page.locator("#filter-type").selectOption("DEDUCTION");
        assertThat(componentRows()).hasCount(1);
        assertThat(componentRow(deduction.getCode()).locator("td").nth(2)).hasText("Potongan");

        page.locator("#filter-type").selectOption("COMPANY_CONTRIBUTION");
        assertThat(componentRows()).hasCount(1);
        assertThat(componentRow(contribution.getCode()).locator("td").nth(4)).hasText("Nonaktif");

        page.locator("#filter-type").selectOption("");
        assertThat(componentRows()).hasCount(3);
        page.locator("#filter-active").selectOption("true");
        assertThat(componentRows()).hasCount(2);
        assertThat(componentRow(earning.getCode())).hasCount(1);
        assertThat(componentRow(deduction.getCode())).hasCount(1);

        page.locator("#filter-active").selectOption("false");
        assertThat(componentRows()).hasCount(1);
        assertThat(componentRow(contribution.getCode())).hasCount(1);
    }

    @Test
    @DisplayName("Should search list by name and code case-insensitively and show empty state")
    void shouldSearchComponentsByKeyword() {
        loginAsAdmin();
        String token = shortId();
        SalaryComponent first = createComponent("Tunjangan Alpha " + token, SalaryComponentType.EARNING, true);
        SalaryComponent second = createComponent("Tunjangan Beta " + token, SalaryComponentType.EARNING, true);

        navigateTo("/salary-components");
        waitForPageLoad();
        search(token.toUpperCase(Locale.ROOT));
        assertThat(componentRows()).hasCount(2);

        search("tunjangan beta " + token);
        assertThat(componentRows()).hasCount(1);
        assertThat(componentRow(second.getCode())).hasCount(1);

        search(first.getCode().toUpperCase(Locale.ROOT));
        assertThat(componentRows()).hasCount(1);
        assertThat(componentRow(first.getCode())).hasCount(1);

        search("tidak-ada-" + token);
        assertThat(componentRows()).hasCount(1);
        assertThat(componentRows().first()).hasText("Tidak ada data komponen gaji");
    }

    @Test
    @DisplayName("Should open new component form with defaults and toggle amount/percentage inputs")
    void shouldDisplayNewSalaryComponentForm() {
        loginAsAdmin();
        navigateTo("/salary-components");
        waitForPageLoad();
        page.locator("#btn-new-component").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/salary-components/new", page.url());
        assertThat(page.locator("#page-title")).hasText("Komponen Gaji Baru");
        assertThat(form()).hasAttribute("action", "/salary-components/new");
        for (String field : new String[]{"code", "name", "description", "defaultAmount", "defaultRate", "displayOrder"}) {
            assertThat(page.locator("#" + field)).hasValue("");
        }
        assertThat(page.locator("#componentType")).hasValue("EARNING");
        assertThat(page.locator("[data-testid='radio-fixed']")).isChecked();
        assertThat(page.locator("input[name='isTaxable'][type='checkbox']")).isChecked();
        assertThat(page.locator("#bpjsCategory")).hasValue("");
        assertThat(page.locator("[data-testid='amount-input-container']")).isVisible();
        assertThat(page.locator("[data-testid='percentage-input-container']")).isHidden();

        page.locator("[data-testid='radio-percentage']").check();
        assertThat(page.locator("[data-testid='percentage-input-container']")).isVisible();
        assertThat(page.locator("[data-testid='amount-input-container']")).isHidden();

        page.locator("[data-testid='radio-fixed']").check();
        assertThat(page.locator("[data-testid='amount-input-container']")).isVisible();
        assertThat(page.locator("[data-testid='percentage-input-container']")).isHidden();
        assertThat(form().locator("a:text-is('Batal')")).hasAttribute("href", "/salary-components");
    }

    @Test
    @DisplayName("Should create fixed-amount component and show it on detail page")
    void shouldCreateFixedAmountComponent() {
        loginAsAdmin();
        String code = "SC-" + shortId();
        String name = "Tunjangan Komunikasi " + code;

        navigateTo("/salary-components/new");
        waitForPageLoad();
        page.locator("#code").fill(code);
        page.locator("#name").fill(name);
        page.locator("#description").fill("Pulsa dan internet bulanan");
        page.locator("#defaultAmount").fill("750000");
        page.locator("#displayOrder").fill("35");
        submit();

        SalaryComponent saved = componentRepository.findByCode(code)
                .orElseThrow(() -> new AssertionError("Component not persisted: " + code));
        assertEquals(baseUrl() + "/salary-components/" + saved.getId(), page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Komponen gaji berhasil ditambahkan");
        assertEquals(name, saved.getName());
        assertEquals("Pulsa dan internet bulanan", saved.getDescription());
        assertEquals(SalaryComponentType.EARNING, saved.getComponentType());
        assertFalse(saved.getIsPercentage());
        assertEquals(0, new BigDecimal("750000").compareTo(saved.getDefaultAmount()));
        assertNull(saved.getDefaultRate());
        assertEquals(35, saved.getDisplayOrder());
        assertTrue(saved.getIsTaxable());
        assertNull(saved.getBpjsCategory());
        assertFalse(saved.getIsSystem());
        assertTrue(saved.getActive());

        assertThat(page.locator("[data-testid='component-name']")).hasText(name);
        assertThat(page.locator("[data-testid='component-code']")).hasText(code);
        assertThat(page.locator("[data-testid='component-type']")).hasText("Pendapatan");
        assertThat(page.locator("[data-testid='active-status']")).hasText("Aktif");
        assertThat(page.locator("[data-testid='value-type']")).hasText("Nominal Tetap");
        assertThat(page.locator("[data-testid='default-amount']")).hasText("Rp 750,000");
        assertThat(page.locator("[data-testid='default-rate']")).hasCount(0);
        assertThat(page.locator("[data-testid='is-taxable']")).hasText("Ya (PPh 21)");
        assertThat(page.locator("[data-testid='bpjs-category']")).hasText("-");
        assertThat(infoValue("Deskripsi")).hasText("Pulsa dan internet bulanan");
        assertThat(infoValue("Urutan Tampil")).hasText("35");
    }

    @Test
    @DisplayName("Should create non-taxable percentage deduction with BPJS category and auto display order")
    void shouldCreatePercentageComponent() {
        loginAsAdmin();
        String code = "SC-" + shortId();
        Integer maxOrder = componentRepository.findMaxDisplayOrder();

        navigateTo("/salary-components/new");
        waitForPageLoad();
        page.locator("#code").fill(code);
        page.locator("#name").fill("Iuran Koperasi " + code);
        page.locator("#componentType").selectOption("DEDUCTION");
        page.locator("[data-testid='radio-percentage']").check();
        page.locator("#defaultRate").fill("2.5");
        page.locator("input[name='isTaxable'][type='checkbox']").uncheck();
        page.locator("#bpjsCategory").selectOption("JP");
        submit();

        SalaryComponent saved = componentRepository.findByCode(code)
                .orElseThrow(() -> new AssertionError("Component not persisted: " + code));
        assertEquals(baseUrl() + "/salary-components/" + saved.getId(), page.url());
        assertEquals(SalaryComponentType.DEDUCTION, saved.getComponentType());
        assertTrue(saved.getIsPercentage());
        assertEquals(0, new BigDecimal("2.5").compareTo(saved.getDefaultRate()));
        assertFalse(saved.getIsTaxable());
        assertEquals("JP", saved.getBpjsCategory());
        assertEquals(maxOrder + 10, saved.getDisplayOrder());

        assertThat(page.locator("[data-testid='component-type']")).hasText("Potongan");
        assertThat(page.locator("[data-testid='value-type']")).hasText("Persentase");
        assertThat(page.locator("[data-testid='default-rate']")).hasText("2.50%");
        assertThat(page.locator("[data-testid='default-amount']")).hasCount(0);
        assertThat(page.locator("[data-testid='is-taxable']")).hasText("Tidak");
        assertThat(page.locator("[data-testid='bpjs-category']")).hasText("JP");
    }

    @Test
    @DisplayName("Should reject percentage component without rate and persist nothing")
    void shouldRejectPercentageWithoutRate() {
        loginAsAdmin();
        String code = "SC-" + shortId();

        navigateTo("/salary-components/new");
        waitForPageLoad();
        page.locator("#code").fill(code);
        page.locator("#name").fill("Tanpa Rate " + code);
        page.locator("[data-testid='radio-percentage']").check();
        submit();

        assertThat(page.locator("#page-title")).hasText("Komponen Gaji Baru");
        assertThat(errorSummary()).hasText("Komponen persentase harus memiliki nilai default rate");
        assertThat(page.locator("[data-testid='radio-percentage']")).isChecked();
        assertThat(page.locator("[data-testid='percentage-input-container']")).isVisible();
        assertFalse(componentRepository.existsByCode(code), "Component persisted without rate");
    }

    @Test
    @DisplayName("Should reject blank code and name and persist nothing")
    void shouldShowValidationErrorForEmptyFields() {
        loginAsAdmin();
        String description = "Kosong " + shortId();

        navigateTo("/salary-components/new");
        waitForPageLoad();
        page.locator("#btn-simpan").evaluate("b => b.form.noValidate = true");
        page.locator("#description").fill(description);
        submit();

        assertThat(page.locator("#page-title")).hasText("Komponen Gaji Baru");
        assertThat(page.locator("#code + p.text-red-500")).hasText("Kode komponen wajib diisi");
        assertThat(page.locator("#name + p.text-red-500")).hasText("Nama komponen wajib diisi");
        assertThat(page.locator("#description")).hasValue(description);
        assertFalse(componentRepository.findAll().stream().anyMatch(c -> description.equals(c.getDescription())),
                "Component persisted despite blank code and name");
    }

    @Test
    @DisplayName("Should reject duplicate code and leave the seeded component unchanged")
    void shouldRejectDuplicateCode() {
        loginAsAdmin();
        String name = "Duplikat " + shortId();

        navigateTo("/salary-components/new");
        waitForPageLoad();
        page.locator("#code").fill("TJ-MAKAN");
        page.locator("#name").fill(name);
        page.locator("#defaultAmount").fill("1000");
        submit();

        assertThat(page.locator("#page-title")).hasText("Komponen Gaji Baru");
        assertThat(page.locator("#code + p.text-red-500")).hasText("Kode komponen sudah digunakan: TJ-MAKAN");
        assertThat(page.locator("#name")).hasValue(name);
        SalaryComponent seeded = seeded("TJ-MAKAN");
        assertEquals("Tunjangan Makan", seeded.getName());
        assertEquals(0, new BigDecimal("500000").compareTo(seeded.getDefaultAmount()));
        assertFalse(componentRepository.findAll().stream().anyMatch(c -> name.equals(c.getName())),
                "Component persisted despite duplicate code");
    }

    @Test
    @DisplayName("Should show seeded system component as read-only with its rate and BPJS category")
    void shouldDisplaySystemComponentDetail() {
        loginAsAdmin();
        SalaryComponent bpjs = seeded("BPJS-KES-P");

        navigateTo("/salary-components");
        waitForPageLoad();
        search("BPJS-KES-P");
        componentRow("BPJS-KES-P").locator("a:text-is('Lihat')").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/salary-components/" + bpjs.getId(), page.url());
        assertThat(page.locator("#page-title")).hasText("Detail Komponen Gaji");
        assertThat(page.locator("[data-testid='component-name']")).hasText("BPJS Kesehatan (Perusahaan)");
        assertThat(page.locator("[data-testid='component-code']")).hasText("BPJS-KES-P");
        assertThat(page.locator("[data-testid='component-type']")).hasText("Kontribusi Perusahaan");
        assertThat(page.locator("[data-testid='value-type']")).hasText("Persentase");
        assertThat(page.locator("[data-testid='default-rate']")).hasText("4.00%");
        assertThat(page.locator("[data-testid='is-taxable']")).hasText("Tidak");
        assertThat(page.locator("[data-testid='bpjs-category']")).hasText("KESEHATAN");
        assertThat(infoValue("Urutan Tampil")).hasText("110");
        assertThat(page.locator("[data-testid='component-detail']"))
                .containsText("Ini adalah komponen sistem yang tidak dapat diubah atau dinonaktifkan.");
        assertThat(detailAction("Edit")).hasCount(0);
        assertThat(page.locator("#form-deactivate")).hasCount(0);
        assertThat(page.locator("form[action$='/activate']")).hasCount(0);
    }

    @Test
    @DisplayName("Should reject editing a system component and leave it unchanged")
    void shouldRejectSystemComponentUpdate() {
        loginAsAdmin();
        SalaryComponent gapok = seeded("GAPOK");

        navigateTo("/salary-components/" + gapok.getId() + "/edit");
        waitForPageLoad();
        assertThat(page.locator("#code")).hasValue("GAPOK");
        page.locator("#name").fill("Gaji Pokok Diubah");
        submit();

        assertThat(page.locator("#page-title")).hasText("Edit Komponen Gaji");
        assertThat(errorSummary()).hasText("Komponen sistem tidak dapat diubah");
        assertThat(form()).hasAttribute("action", "/salary-components/" + gapok.getId());
        assertEquals("Gaji Pokok", seeded("GAPOK").getName());
    }

    @Test
    @DisplayName("Should open edit form prefilled with the component's values")
    void shouldDisplaySalaryComponentEditForm() {
        loginAsAdmin();
        SalaryComponent component = createComponent("Edit " + shortId(), SalaryComponentType.EARNING, true);

        navigateTo("/salary-components/" + component.getId());
        waitForPageLoad();
        detailAction("Edit").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/salary-components/" + component.getId() + "/edit", page.url());
        assertThat(page.locator("#page-title")).hasText("Edit Komponen Gaji");
        assertThat(form()).hasAttribute("action", "/salary-components/" + component.getId());
        assertThat(page.locator("#code")).hasValue(component.getCode());
        assertThat(page.locator("#name")).hasValue(component.getName());
        assertThat(page.locator("#description")).hasValue(component.getDescription());
        assertThat(page.locator("#componentType")).hasValue("EARNING");
        assertThat(page.locator("[data-testid='radio-fixed']")).isChecked();
        assertThat(page.locator("[data-testid='amount-input-container']")).isVisible();
        assertThat(page.locator("#defaultAmount")).hasValue("250000.00");
        assertThat(page.locator("#displayOrder")).hasValue(component.getDisplayOrder().toString());
        assertThat(page.locator("input[name='isTaxable'][type='checkbox']")).isChecked();
        assertThat(form().locator("a:text-is('Batal')")).hasAttribute("href", "/salary-components/" + component.getId());
    }

    @Test
    @DisplayName("Should update code, name, type, value type, tax flag and BPJS category")
    void shouldUpdateSalaryComponent() {
        loginAsAdmin();
        SalaryComponent component = createComponent("Ubah " + shortId(), SalaryComponentType.EARNING, true);
        String newCode = "SC-" + shortId();
        String newName = "Diperbarui " + newCode;

        navigateTo("/salary-components/" + component.getId() + "/edit");
        waitForPageLoad();
        page.locator("#code").fill(newCode);
        page.locator("#name").fill(newName);
        page.locator("#componentType").selectOption("COMPANY_CONTRIBUTION");
        page.locator("[data-testid='radio-percentage']").check();
        page.locator("#defaultRate").fill("0.54");
        page.locator("input[name='isTaxable'][type='checkbox']").uncheck();
        page.locator("#bpjsCategory").selectOption("JKK");
        submit();

        assertEquals(baseUrl() + "/salary-components/" + component.getId(), page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Komponen gaji berhasil diperbarui");
        assertThat(page.locator("[data-testid='component-name']")).hasText(newName);
        assertThat(page.locator("[data-testid='default-rate']")).hasText("0.54%");

        SalaryComponent updated = componentRepository.findById(component.getId()).orElseThrow();
        assertEquals(newCode, updated.getCode());
        assertEquals(newName, updated.getName());
        assertEquals(SalaryComponentType.COMPANY_CONTRIBUTION, updated.getComponentType());
        assertTrue(updated.getIsPercentage());
        assertEquals(0, new BigDecimal("0.54").compareTo(updated.getDefaultRate()));
        assertFalse(updated.getIsTaxable());
        assertEquals("JKK", updated.getBpjsCategory());
        assertEquals(component.getDescription(), updated.getDescription());
        assertEquals(component.getDisplayOrder(), updated.getDisplayOrder());
        assertFalse(componentRepository.existsByCode(component.getCode()), "Old code still present");
    }

    @Test
    @DisplayName("Should keep component active when deactivate confirmation is dismissed")
    void shouldKeepComponentActiveWhenDeactivateDismissed() {
        loginAsAdmin();
        SalaryComponent component = createComponent("Batal " + shortId(), SalaryComponentType.EARNING, true);

        navigateTo("/salary-components/" + component.getId());
        waitForPageLoad();
        page.onceDialog(dialog -> {
            assertEquals("Nonaktifkan komponen ini?", dialog.message());
            dialog.dismiss();
        });
        page.locator("#form-deactivate button[type='submit']").click();

        assertThat(page.locator("[data-testid='active-status']")).hasText("Aktif");
        assertTrue(componentRepository.findById(component.getId()).orElseThrow().getActive());
    }

    @Test
    @DisplayName("Should deactivate component after confirmation")
    void shouldDeactivateSalaryComponent() {
        loginAsAdmin();
        SalaryComponent component = createComponent("Nonaktif " + shortId(), SalaryComponentType.EARNING, true);

        navigateTo("/salary-components/" + component.getId());
        waitForPageLoad();
        page.onceDialog(dialog -> dialog.accept());
        page.locator("#form-deactivate button[type='submit']").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/salary-components/" + component.getId(), page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Komponen gaji berhasil dinonaktifkan");
        assertThat(page.locator("[data-testid='active-status']")).hasText("Nonaktif");
        assertThat(page.locator("#form-deactivate")).hasCount(0);
        assertThat(page.locator("form[action$='/activate']")).hasCount(1);
        assertFalse(componentRepository.findById(component.getId()).orElseThrow().getActive());
    }

    @Test
    @DisplayName("Should activate inactive component")
    void shouldActivateSalaryComponent() {
        loginAsAdmin();
        SalaryComponent component = createComponent("Aktifkan " + shortId(), SalaryComponentType.EARNING, false);

        navigateTo("/salary-components/" + component.getId());
        waitForPageLoad();
        assertThat(page.locator("[data-testid='active-status']")).hasText("Nonaktif");
        page.locator("form[action$='/activate'] button[type='submit']").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/salary-components/" + component.getId(), page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Komponen gaji berhasil diaktifkan");
        assertThat(page.locator("[data-testid='active-status']")).hasText("Aktif");
        assertThat(page.locator("#form-deactivate")).hasCount(1);
        assertTrue(componentRepository.findById(component.getId()).orElseThrow().getActive());
    }

    @Test
    @DisplayName("Should let a view-only user browse components but not create, edit or deactivate them")
    void shouldDenyChangesWithoutManagePermission() {
        SalaryComponent component = createComponent("Auditor " + shortId(), SalaryComponentType.EARNING, true);
        User auditor = createUser(Role.AUDITOR);
        login(auditor.getUsername(), "password");

        navigateTo("/salary-components");
        waitForPageLoad();
        search(component.getCode());
        assertThat(componentRows()).hasCount(1);
        assertThat(page.locator("#btn-new-component")).hasCount(0);

        navigateTo("/salary-components/" + component.getId());
        waitForPageLoad();
        assertThat(page.locator("[data-testid='component-name']")).hasText(component.getName());
        assertThat(detailAction("Edit")).hasCount(0);
        assertThat(page.locator("#form-deactivate")).hasCount(0);
        String csrf = page.locator("meta[name='_csrf']").getAttribute("content");

        navigateTo("/salary-components/new");
        waitForPageLoad();
        assertThat(page.locator("#access-denied-page")).isVisible();

        navigateTo("/salary-components/" + component.getId() + "/edit");
        waitForPageLoad();
        assertThat(page.locator("#access-denied-page")).isVisible();

        String code = "SC-" + shortId();
        APIResponse create = page.request().post(baseUrl() + "/salary-components/new", RequestOptions.create()
                .setMaxRedirects(0)
                .setForm(FormData.create().set("_csrf", csrf).set("code", code).set("name", "Ilegal " + code)
                        .set("componentType", "EARNING").set("isPercentage", "false").set("defaultAmount", "1")));
        assertEquals(403, create.status());
        assertFalse(componentRepository.existsByCode(code), "View-only user created a component");

        APIResponse update = page.request().post(baseUrl() + "/salary-components/" + component.getId(),
                RequestOptions.create().setMaxRedirects(0)
                        .setForm(FormData.create().set("_csrf", csrf).set("code", component.getCode())
                                .set("name", "Diubah Auditor").set("componentType", "EARNING")
                                .set("isPercentage", "false").set("defaultAmount", "1")));
        assertEquals(403, update.status());

        APIResponse deactivate = page.request().post(baseUrl() + "/salary-components/" + component.getId()
                + "/deactivate", RequestOptions.create().setMaxRedirects(0)
                .setForm(FormData.create().set("_csrf", csrf)));
        assertEquals(403, deactivate.status());

        SalaryComponent unchanged = componentRepository.findById(component.getId()).orElseThrow();
        assertEquals(component.getName(), unchanged.getName());
        assertTrue(unchanged.getActive());
    }

    // ==================== helpers ====================

    private SalaryComponent createComponent(String name, SalaryComponentType type, boolean active) {
        String id = shortId();
        SalaryComponent c = new SalaryComponent();
        c.setCode("SC-" + id);
        c.setName(name);
        c.setDescription("Deskripsi " + id);
        c.setComponentType(type);
        c.setIsPercentage(false);
        c.setDefaultAmount(new BigDecimal("250000"));
        c.setIsTaxable(true);
        SalaryComponent saved = componentService.create(c);
        if (!active) {
            componentService.deactivate(saved.getId());
            saved.setActive(false);
        }
        return saved;
    }

    private User createUser(Role role) {
        String id = shortId();
        User user = new User();
        user.setUsername("sc-user-" + id);
        user.setPassword(passwordEncoder.encode("password"));
        user.setFullName("Pengguna " + id);
        user.setEmail("sc-user-" + id + "@example.com");
        user.setActive(true);
        user.addRole(role, "system");
        return userRepository.save(user);
    }

    private SalaryComponent seeded(String code) {
        return componentRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Seeded salary component missing: " + code));
    }

    private void search(String keyword) {
        Locator input = page.locator("#search-input");
        input.fill(keyword);
        input.press("End");
    }

    private Locator componentRows() {
        return page.locator("#component-table tbody tr");
    }

    private Locator componentRow(String code) {
        return page.locator("#component-table tbody tr:has(td:first-child span:text-is('" + code + "'))");
    }

    private Locator infoValue(String label) {
        return page.locator("[data-testid='component-detail'] div:has(> p.text-gray-500:text-is('" + label
                + "')) > p.font-medium");
    }

    private Locator detailAction(String text) {
        return page.locator("[data-testid='component-detail'] a:text-is('" + text + "')");
    }

    private Locator errorSummary() {
        return form().locator("ul.list-disc li");
    }

    private Locator form() {
        return page.locator("form:has(#btn-simpan)");
    }

    private void submit() {
        page.locator("#btn-simpan").click();
        waitForPageLoad();
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
