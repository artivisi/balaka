package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.Client;
import com.artivisi.accountingfinance.entity.Project;
import com.artivisi.accountingfinance.enums.ProjectStatus;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ClientRepository;
import com.artivisi.accountingfinance.repository.ProjectRepository;
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
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * ProjectController: list with search/status/client filters, new/edit form, create/update with
 * validation, detail page, and complete/archive/reactivate status transitions.
 *
 * Mutating tests create their own projects (unique code) so they do not depend on or disturb
 * the seeded projects other test classes use. Milestone and payment term sections on the detail
 * page are covered by MilestoneControllerFunctionalTest, PaymentTermControllerFunctionalTest and
 * ServiceMilestonePaymentTermTest.
 */
@DisplayName("Project Controller Tests")
@Import(ServiceTestDataInitializer.class)
class ProjectControllerFunctionalTest extends PlaywrightTestBase {

    // From src/test/resources/testdata/service/clients.csv and projects.csv
    private static final String MANDIRI = "MANDIRI";
    private static final String TELKOM = "TELKOM";
    private static final String SEEDED_PROJECT_CODE = "PRJ-2024-001";
    private static final DateTimeFormatter DISPLAY_DATE =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.forLanguageTag("id-ID"));

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ClientRepository clientRepository;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Should list seeded project with client, contract value and status")
    void shouldDisplayProjectListPage() {
        navigateTo("/projects?search=" + SEEDED_PROJECT_CODE);
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Daftar Proyek");
        assertThat(page.locator("#btn-new-project")).hasAttribute("href", "/projects/new");
        assertThat(page.locator("#search-input")).hasValue(SEEDED_PROJECT_CODE);

        Locator rows = page.locator("[data-testid='project-table'] tbody tr");
        assertThat(rows).hasCount(1);
        Locator cells = rows.first().locator("td");
        assertThat(cells.nth(0)).hasText(SEEDED_PROJECT_CODE);
        assertThat(cells.nth(1)).hasText("Core Banking System Consultation");
        assertThat(cells.nth(1).locator("a")).hasAttribute("href", "/projects/" + SEEDED_PROJECT_CODE);
        assertThat(cells.nth(2)).hasText("PT Bank Mandiri Tbk");
        assertThat(cells.nth(3)).hasText("600,000,000");
        assertThat(cells.nth(4)).hasText("Aktif");

        navigateTo("/projects");
        waitForPageLoad();
        takeManualScreenshot("service/projects-list");
    }

    @Test
    @DisplayName("Should narrow list by keyword on code or name, case-insensitive")
    void shouldSearchProjectsByKeyword() {
        String token = shortId();
        Project first = createProject("Alpha " + token, MANDIRI);
        Project second = createProject("Beta " + token, TELKOM);

        navigateTo("/projects");
        waitForPageLoad();
        search(token.toUpperCase(Locale.ROOT));
        assertThat(projectRows()).hasCount(2);
        assertThat(projectRow(first)).hasCount(1);
        assertThat(projectRow(second)).hasCount(1);

        search(first.getCode().toLowerCase(Locale.ROOT));
        assertThat(projectRows()).hasCount(1);
        assertThat(projectRow(first)).hasCount(1);

        search("no-such-project-" + token);
        assertThat(projectRows()).hasCount(1);
        assertThat(projectRows().first()).hasText("Tidak ada proyek");
    }

    @Test
    @DisplayName("Should filter list by status")
    void shouldFilterProjectsByStatus() {
        String token = shortId();
        Project active = createProject("Aktif " + token, MANDIRI);
        Project completed = createProject("Selesai " + token, MANDIRI);
        projectService.complete(completed.getId());

        navigateTo("/projects");
        waitForPageLoad();
        search(token);
        assertThat(projectRows()).hasCount(2);

        page.locator("#status-filter").selectOption("COMPLETED");
        assertThat(projectRows()).hasCount(1);
        assertThat(projectRow(completed).locator("td").nth(4)).hasText("Selesai");

        page.locator("#status-filter").selectOption("ACTIVE");
        assertThat(projectRows()).hasCount(1);
        assertThat(projectRow(active).locator("td").nth(4)).hasText("Aktif");

        page.locator("#status-filter").selectOption("ARCHIVED");
        assertThat(projectRows()).hasCount(1);
        assertThat(projectRows().first()).hasText("Tidak ada proyek");
    }

    @Test
    @DisplayName("Should filter list by client picked in combobox")
    void shouldFilterProjectsByClient() {
        String token = shortId();
        createProject("Mandiri " + token, MANDIRI);
        Project telkom = createProject("Telkom " + token, TELKOM);

        navigateTo("/projects");
        waitForPageLoad();
        search(token);
        assertThat(projectRows()).hasCount(2);

        pickClient("#client-picker-input", TELKOM);

        assertThat(projectRows()).hasCount(1);
        assertThat(projectRow(telkom).locator("td").nth(2)).hasText("PT Telkom Indonesia Tbk");
        assertThat(page.locator("#client-picker-input")).hasValue(TELKOM + " - PT Telkom Indonesia Tbk");
    }

    @Test
    @DisplayName("Should open empty new project form")
    void shouldDisplayNewProjectForm() {
        navigateTo("/projects");
        waitForPageLoad();
        page.locator("#btn-new-project").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/projects/new", page.url());
        assertThat(page.locator("#page-title")).hasText("Proyek Baru");
        assertThat(form()).hasAttribute("action", "/projects/new");
        assertThat(page.locator("#code")).hasValue("");
        assertThat(page.locator("#name")).hasValue("");
        assertThat(page.locator("#clientId")).hasValue("");
        assertThat(page.locator("input[type='hidden'][name='clientId']")).hasValue("");
        assertThat(page.locator("#contractValue")).hasValue("");
        assertThat(page.locator("#budgetAmount")).hasValue("");
        assertThat(page.locator("#startDate")).hasValue("");
        assertThat(page.locator("#endDate")).hasValue("");
        assertThat(page.locator("#description")).hasValue("");
        assertThat(form().locator("a:text-is('Batal')")).hasAttribute("href", "/projects");
    }

    @Test
    @DisplayName("Should create project with client, values and dates and show it on detail page")
    void shouldCreateNewProject() {
        String code = "PC-" + shortId();
        String name = "Proyek Baru " + code;
        LocalDate start = LocalDate.of(2027, 1, 10);
        LocalDate end = LocalDate.of(2027, 6, 30);

        navigateTo("/projects/new");
        waitForPageLoad();
        page.locator("#code").fill(code);
        page.locator("#name").fill(name);
        pickClient("#clientId", MANDIRI);
        page.locator("#contractValue").fill("250000000");
        page.locator("#budgetAmount").fill("180000000");
        page.locator("#startDate").fill(start.toString());
        page.locator("#endDate").fill(end.toString());
        page.locator("#description").fill("Deskripsi " + code);
        submit();

        assertEquals(baseUrl() + "/projects/" + code, page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Proyek berhasil ditambahkan");

        Project saved = projectRepository.findByCode(code)
                .orElseThrow(() -> new AssertionError("Project not persisted: " + code));
        assertEquals(name, saved.getName());
        assertEquals(seededClient(MANDIRI).getId(), saved.getClient().getId());
        assertEquals(0, new BigDecimal("250000000").compareTo(saved.getContractValue()));
        assertEquals(0, new BigDecimal("180000000").compareTo(saved.getBudgetAmount()));
        assertEquals(start, saved.getStartDate());
        assertEquals(end, saved.getEndDate());
        assertEquals("Deskripsi " + code, saved.getDescription());
        assertEquals(ProjectStatus.ACTIVE, saved.getStatus());

        Locator detail = page.locator("[data-testid='project-detail']");
        assertThat(detail.locator("h2")).hasText(name);
        assertThat(detail).containsText("Klien: PT Bank Mandiri Tbk");
        assertThat(detail).containsText("Rp 250,000,000");
        assertThat(detail).containsText("Rp 180,000,000");
        assertThat(detail).containsText(DISPLAY_DATE.format(start));
        assertThat(detail).containsText(DISPLAY_DATE.format(end));
        assertThat(detail).containsText("Deskripsi " + code);
    }

    @Test
    @DisplayName("Should reject duplicate code, keep entered values and client, persist nothing")
    void shouldShowValidationErrorForDuplicateCode() {
        Project existing = createProject("Existing " + shortId(), TELKOM);
        String name = "Duplicate " + shortId();

        navigateTo("/projects/new");
        waitForPageLoad();
        page.locator("#code").fill(existing.getCode());
        page.locator("#name").fill(name);
        pickClient("#clientId", MANDIRI);
        page.locator("#startDate").fill("2027-02-01");
        submit();

        assertEquals(baseUrl() + "/projects/new", page.url());
        assertThat(page.locator("#page-title")).hasText("Proyek Baru");
        assertThat(page.locator("#code + p.text-red-500"))
                .hasText("Project code already exists: " + existing.getCode());
        assertThat(page.locator("#name")).hasValue(name);
        assertThat(page.locator("#startDate")).hasValue("2027-02-01");
        assertThat(page.locator("#clientId")).hasValue(MANDIRI + " - PT Bank Mandiri Tbk");
        assertThat(page.locator("input[type='hidden'][name='clientId']"))
                .hasValue(seededClient(MANDIRI).getId().toString());

        Project unchanged = projectRepository.findByCode(existing.getCode()).orElseThrow();
        assertEquals(existing.getName(), unchanged.getName());
        assertEquals(seededClient(TELKOM).getId(), unchanged.getClient().getId());
        assertFalse(projectRepository.findAll().stream().anyMatch(p -> name.equals(p.getName())),
                "Project persisted despite duplicate code");
    }

    @Test
    @DisplayName("Should reject blank name and persist nothing")
    void shouldRejectBlankName() {
        String code = "PC-" + shortId();

        navigateTo("/projects/new");
        waitForPageLoad();
        page.locator("#btn-simpan").evaluate("b => b.form.noValidate = true");
        page.locator("#code").fill(code);
        submit();

        assertThat(page.locator("#page-title")).hasText("Proyek Baru");
        assertThat(page.locator("#name + p.text-red-500")).hasText("Nama proyek wajib diisi");
        assertThat(page.locator("#code")).hasValue(code);
        assertFalse(projectRepository.existsByCode(code), "Project persisted despite blank name");
    }

    @Test
    @DisplayName("Should show seeded project details on detail page")
    void shouldDisplayProjectDetailPage() {
        navigateTo("/projects");
        waitForPageLoad();
        search(SEEDED_PROJECT_CODE);
        assertThat(projectRows()).hasCount(1);
        projectRows().first().locator("a:text-is('Detail')").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/projects/" + SEEDED_PROJECT_CODE, page.url());
        assertThat(page.locator("#page-title")).hasText("Detail Proyek");
        Locator detail = page.locator("[data-testid='project-detail']");
        assertThat(detail.locator("h2")).hasText("Core Banking System Consultation");
        assertThat(detail.locator("p.font-mono")).hasText(SEEDED_PROJECT_CODE);
        assertThat(detail.locator("a[href='/clients/" + MANDIRI + "']")).hasText("PT Bank Mandiri Tbk");
        assertStatusBadge("Aktif");
        assertThat(detail).containsText("Rp 600,000,000");
        assertThat(detail).containsText(DISPLAY_DATE.format(LocalDate.of(2024, 1, 1)));
        assertThat(detail).containsText(DISPLAY_DATE.format(LocalDate.of(2024, 6, 30)));
        assertThat(detail).containsText("Konsultasi arsitektur sistem core banking");
        assertThat(detail.locator("a:text-is('Edit')")).hasAttribute("href", "/projects/" + SEEDED_PROJECT_CODE + "/edit");
        assertThat(page.locator("#form-complete")).hasCount(1);
        assertThat(page.locator("#form-archive")).hasCount(1);
        assertThat(page.locator("form[action$='/reactivate']")).hasCount(0);
    }

    @Test
    @DisplayName("Should open edit form prefilled with project values including dates and client")
    void shouldDisplayProjectEditForm() {
        Project project = createProject("Edit " + shortId(), MANDIRI);

        navigateTo("/projects/" + project.getCode());
        waitForPageLoad();
        page.locator("[data-testid='project-detail'] a:text-is('Edit')").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/projects/" + project.getCode() + "/edit", page.url());
        assertThat(page.locator("#page-title")).hasText("Edit Proyek");
        assertThat(form()).hasAttribute("action", "/projects/" + project.getCode());
        assertThat(page.locator("#code")).hasValue(project.getCode());
        assertThat(page.locator("#name")).hasValue(project.getName());
        assertThat(page.locator("#clientId")).hasValue(MANDIRI + " - PT Bank Mandiri Tbk");
        assertThat(page.locator("input[type='hidden'][name='clientId']"))
                .hasValue(seededClient(MANDIRI).getId().toString());
        assertThat(page.locator("#contractValue")).hasValue("100000000.00");
        assertThat(page.locator("#budgetAmount")).hasValue("80000000.00");
        assertThat(page.locator("#startDate")).hasValue("2027-01-15");
        assertThat(page.locator("#endDate")).hasValue("2027-12-31");
        assertThat(page.locator("#description")).hasValue("Deskripsi " + project.getCode());
        assertThat(form().locator("a:text-is('Batal')")).hasAttribute("href", "/projects/" + project.getCode());
    }

    @Test
    @DisplayName("Should update name, client and end date and keep untouched start date")
    void shouldUpdateProject() {
        Project project = createProject("Update " + shortId(), MANDIRI);
        String newName = "Updated " + shortId();

        navigateTo("/projects/" + project.getCode() + "/edit");
        waitForPageLoad();
        page.locator("#name").fill(newName);
        page.locator("#clientId").fill("");
        pickClient("#clientId", TELKOM);
        page.locator("#endDate").fill("2028-03-31");
        submit();

        assertEquals(baseUrl() + "/projects/" + project.getCode(), page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Proyek berhasil diperbarui");
        assertThat(page.locator("[data-testid='project-detail'] h2")).hasText(newName);
        assertThat(page.locator("[data-testid='project-detail']")).containsText("Klien: PT Telkom Indonesia Tbk");

        Project updated = projectRepository.findByCode(project.getCode()).orElseThrow();
        assertEquals(newName, updated.getName());
        assertEquals(seededClient(TELKOM).getId(), updated.getClient().getId());
        assertEquals(LocalDate.of(2027, 1, 15), updated.getStartDate());
        assertEquals(LocalDate.of(2028, 3, 31), updated.getEndDate());
        assertEquals(0, new BigDecimal("100000000").compareTo(updated.getContractValue()));
        assertEquals(0, new BigDecimal("80000000").compareTo(updated.getBudgetAmount()));
    }

    @Test
    @DisplayName("Should reject update to another project's code and keep the form bound to the edited project")
    void shouldRejectUpdateToDuplicateCode() {
        Project project = createProject("Ubah " + shortId(), MANDIRI);
        Project other = createProject("Lain " + shortId(), TELKOM);
        String fixedCode = "PC-" + shortId();
        String newName = "Diperbaiki " + shortId();

        navigateTo("/projects/" + project.getCode() + "/edit");
        waitForPageLoad();
        page.locator("#code").fill(other.getCode());
        submit();

        assertThat(page.locator("#page-title")).hasText("Edit Proyek");
        assertThat(page.locator("#code + p.text-red-500"))
                .hasText("Project code already exists: " + other.getCode());
        assertThat(page.locator("#code")).hasValue(other.getCode());
        assertThat(form()).hasAttribute("action", "/projects/" + project.getCode());
        assertThat(form().locator("a:text-is('Batal')")).hasAttribute("href", "/projects/" + project.getCode());

        page.locator("#code").fill(fixedCode);
        page.locator("#name").fill(newName);
        submit();

        assertEquals(baseUrl() + "/projects/" + fixedCode, page.url());
        Project updated = projectRepository.findByCode(fixedCode).orElseThrow();
        assertEquals(project.getId(), updated.getId());
        assertEquals(newName, updated.getName());
        assertFalse(projectRepository.existsByCode(project.getCode()), "Edited project kept its old code");
        Project untouched = projectRepository.findByCode(other.getCode()).orElseThrow();
        assertEquals(other.getId(), untouched.getId());
        assertEquals(other.getName(), untouched.getName());
        assertEquals(seededClient(TELKOM).getId(), untouched.getClient().getId());
    }

    @Test
    @DisplayName("Should keep project active when complete confirmation is dismissed")
    void shouldKeepProjectActiveWhenCompleteDismissed() {
        Project project = createProject("Dismiss " + shortId(), MANDIRI);

        navigateTo("/projects/" + project.getCode());
        waitForPageLoad();
        page.onceDialog(dialog -> {
            assertEquals("Tandai proyek ini selesai?", dialog.message());
            dialog.dismiss();
        });
        page.locator("#form-complete button").click();
        waitForPageLoad();

        assertThat(page.locator("[data-testid='flash-success']")).hasCount(0);
        assertThat(page.locator("#form-complete")).hasCount(1);
        assertEquals(ProjectStatus.ACTIVE, projectRepository.findByCode(project.getCode()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("Should complete active project")
    void shouldCompleteProject() {
        Project project = createProject("Complete " + shortId(), MANDIRI);

        navigateTo("/projects/" + project.getCode());
        waitForPageLoad();
        page.onceDialog(dialog -> {
            assertEquals("Tandai proyek ini selesai?", dialog.message());
            dialog.accept();
        });
        page.locator("#form-complete button").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/projects/" + project.getCode(), page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Proyek berhasil diselesaikan");
        assertStatusBadge("Selesai");
        assertThat(page.locator("#form-complete")).hasCount(0);
        assertThat(page.locator("#form-archive")).hasCount(0);
        assertThat(page.locator("form[action$='/reactivate']")).hasCount(1);
        assertThat(page.locator("#btn-new-milestone")).hasCount(0);
        assertEquals(ProjectStatus.COMPLETED, projectRepository.findByCode(project.getCode()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("Should archive active project")
    void shouldArchiveProject() {
        Project project = createProject("Archive " + shortId(), MANDIRI);

        navigateTo("/projects/" + project.getCode());
        waitForPageLoad();
        page.onceDialog(dialog -> {
            assertEquals("Arsipkan proyek ini?", dialog.message());
            dialog.accept();
        });
        page.locator("#form-archive button").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/projects/" + project.getCode(), page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Proyek berhasil diarsipkan");
        assertStatusBadge("Diarsipkan");
        assertThat(page.locator("#form-complete")).hasCount(0);
        assertThat(page.locator("#form-archive")).hasCount(0);
        assertThat(page.locator("form[action$='/reactivate']")).hasCount(1);
        assertEquals(ProjectStatus.ARCHIVED, projectRepository.findByCode(project.getCode()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("Should reactivate completed project")
    void shouldReactivateProject() {
        Project project = createProject("Reactivate " + shortId(), MANDIRI);
        projectService.complete(project.getId());

        navigateTo("/projects/" + project.getCode());
        waitForPageLoad();
        assertStatusBadge("Selesai");
        page.locator("form[action$='/reactivate'] button").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/projects/" + project.getCode(), page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Proyek berhasil diaktifkan kembali");
        assertStatusBadge("Aktif");
        assertThat(page.locator("#form-complete")).hasCount(1);
        assertThat(page.locator("#form-archive")).hasCount(1);
        assertThat(page.locator("form[action$='/reactivate']")).hasCount(0);
        assertEquals(ProjectStatus.ACTIVE, projectRepository.findByCode(project.getCode()).orElseThrow().getStatus());
    }

    // ==================== helpers ====================

    private Project createProject(String name, String clientCode) {
        Project p = new Project();
        p.setCode("PC-" + shortId());
        p.setName(name);
        p.setContractValue(new BigDecimal("100000000"));
        p.setBudgetAmount(new BigDecimal("80000000"));
        p.setStartDate(LocalDate.of(2027, 1, 15));
        p.setEndDate(LocalDate.of(2027, 12, 31));
        p.setDescription("Deskripsi " + p.getCode());
        return projectService.create(p, seededClient(clientCode).getId());
    }

    private Client seededClient(String code) {
        return clientRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Seeded client missing: " + code));
    }

    private void pickClient(String inputSelector, String clientCode) {
        Locator input = page.locator(inputSelector);
        input.click();
        input.fill(clientCode);
        Locator result = page.locator("[data-testid='client-picker-result']:has(span.font-medium:text-is('"
                + clientCode + "'))");
        assertThat(result).hasCount(1);
        result.click();
        assertThat(input).hasValue(clientCode + " - " + seededClient(clientCode).getName());
    }

    private void search(String keyword) {
        Locator input = page.locator("#search-input");
        input.fill(keyword);
        input.press("End");
    }

    private Locator projectRows() {
        return page.locator("[data-testid='project-table'] tbody tr");
    }

    private Locator projectRow(Project project) {
        return page.locator("[data-testid='project-table'] tbody tr[data-id='" + project.getId() + "']");
    }

    private void assertStatusBadge(String text) {
        assertThat(page.locator("[data-testid='project-status'] span")).hasText(text);
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
