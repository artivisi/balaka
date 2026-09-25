package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.AssetCategory;
import com.artivisi.accountingfinance.entity.Client;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.AssetCategoryRepository;
import com.artivisi.accountingfinance.repository.ClientRepository;
import com.artivisi.accountingfinance.repository.EmployeeRepository;
import com.artivisi.accountingfinance.repository.FixedAssetRepository;
import com.artivisi.accountingfinance.repository.InvoiceRepository;
import com.artivisi.accountingfinance.repository.ProjectRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Server-side validation of form fields not covered by the per-feature controller tests.
 * Browser constraint validation is switched off before submitting so the request reaches
 * the server; each test asserts the rendered message and that nothing was persisted.
 */
@DisplayName("Validation Tests")
@Import(ServiceTestDataInitializer.class)
class ValidationTest extends PlaywrightTestBase {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private FixedAssetRepository fixedAssetRepository;

    @Autowired
    private AssetCategoryRepository assetCategoryRepository;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Employee form rejects a malformed email and persists nothing")
    void employeeFormRejectsMalformedEmail() {
        String employeeId = "VAL-" + uid();
        navigateTo("/employees/new");
        waitForPageLoad();
        disableBrowserValidation();
        page.locator("#employeeId").fill(employeeId);
        page.locator("#name").fill("Validasi " + employeeId);
        page.locator("#email").fill("invalid-email");
        page.locator("#hireDate").fill("2024-01-02");
        submit();

        assertThat(page.locator("#page-title")).hasText("Karyawan Baru");
        assertThat(page.locator("#email + p.text-red-500")).hasText("Format email tidak valid");
        assertThat(page.locator("#email")).hasValue("invalid-email");
        assertThat(page.locator("#employeeId")).hasValue(employeeId);
        assertThat(employeeRepository.existsByEmployeeId(employeeId)).isFalse();
    }

    @Test
    @DisplayName("Project form rejects a blank code and persists nothing")
    void projectFormRejectsBlankCode() {
        String name = "Validasi Proyek " + uid();
        navigateTo("/projects/new");
        waitForPageLoad();
        disableBrowserValidation();
        page.locator("#name").fill(name);
        submit();

        assertThat(page.locator("#page-title")).hasText("Proyek Baru");
        assertThat(page.locator("#code + p.text-red-500")).hasText("Kode proyek wajib diisi");
        assertThat(page.locator("#name")).hasValue(name);
        assertThat(projectRepository.findAll()).noneMatch(p -> name.equals(p.getName()));
    }

    @Test
    @DisplayName("Invoice form rejects a negative amount and persists nothing")
    void invoiceFormRejectsNegativeAmount() {
        Client client = new Client();
        client.setCode("VAL-" + uid());
        client.setName("Klien Validasi " + client.getCode());
        client = clientRepository.save(client);

        navigateTo("/invoices/new");
        waitForPageLoad();
        Locator clientInput = page.locator("#clientLabel");
        clientInput.click();
        clientInput.fill(client.getCode());
        Locator result = page.locator("[data-testid='client-picker-result']",
                new Page.LocatorOptions().setHasText(client.getCode()));
        assertThat(result).hasCount(1);
        result.click();
        page.locator("#invoiceDate").fill("2024-02-01");
        page.locator("#dueDate").fill("2024-03-02");
        page.locator("#amount").fill("-1000");
        disableBrowserValidation();
        submit();

        assertThat(page).hasURL(baseUrl() + "/invoices/new");
        assertThat(page.locator("form li")).hasText("Jumlah tidak boleh negatif");
        assertThat(page.locator("#amount")).hasValue("-1000");
        assertThat(invoiceRepository.findByClientId(client.getId())).isEmpty();
    }

    @Test
    @DisplayName("Asset form rejects a blank name and persists nothing")
    void assetFormRejectsBlankName() {
        String code = "VAL-" + uid();
        fillAssetForm(code, "", "5000000");
        submit();

        assertThat(page.locator("#page-title")).hasText("Aset Baru");
        assertThat(page.locator("#name ~ p.text-red-500")).hasText("Nama aset wajib diisi");
        assertThat(page.locator("#assetCode")).hasValue(code);
        assertThat(fixedAssetRepository.findByAssetCode(code)).isEmpty();
    }

    @Test
    @DisplayName("Asset form rejects a zero purchase cost (residual not below cost) and persists nothing")
    void assetFormRejectsZeroPurchaseCost() {
        String code = "VAL-" + uid();
        fillAssetForm(code, "Aset Nol " + code, "0");
        submit();

        assertThat(page.locator("#page-title")).hasText("Aset Baru");
        assertThat(page.locator("form:has(#btn-simpan) ul li"))
                .hasText(new String[]{"Nilai residu harus lebih kecil dari nilai perolehan"});
        assertThat(page.locator("#assetCode")).hasValue(code);
        assertThat(fixedAssetRepository.findByAssetCode(code)).isEmpty();
    }

    private void fillAssetForm(String code, String name, String cost) {
        AssetCategory category = assetCategoryRepository.findByCode("KOMPUTER")
                .orElseThrow(() -> new IllegalStateException("Seeded asset category KOMPUTER missing"));
        navigateTo("/assets/new");
        waitForPageLoad();
        disableBrowserValidation();
        page.locator("#assetCode").fill(code);
        page.locator("#name").fill(name);
        page.locator("#category").selectOption(category.getId().toString());
        page.locator("#fundingAccount").pressSequentially("1.1.02");
        page.locator("[data-testid='account-picker-result']:has-text('Bank BCA')").click();
        assertThat(page.locator("#fundingAccount")).hasValue("1.1.02 - Bank BCA");
        page.locator("#purchaseDate").fill(LocalDate.of(2024, 1, 10).toString());
        page.locator("#purchaseCost").fill(cost);
        page.locator("#depreciationStartDate").fill(LocalDate.of(2024, 1, 1).toString());
        page.locator("#usefulLifeMonths").fill("24");
    }

    private void disableBrowserValidation() {
        page.locator("#btn-simpan").evaluate("b => b.form.noValidate = true");
    }

    private void submit() {
        page.locator("#btn-simpan").click();
        waitForPageLoad();
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
