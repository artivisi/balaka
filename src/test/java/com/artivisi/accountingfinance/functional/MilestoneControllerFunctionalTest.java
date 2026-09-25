package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.Project;
import com.artivisi.accountingfinance.entity.ProjectMilestone;
import com.artivisi.accountingfinance.enums.MilestoneStatus;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ClientRepository;
import com.artivisi.accountingfinance.repository.ProjectMilestoneRepository;
import com.artivisi.accountingfinance.service.ProjectMilestoneService;
import com.artivisi.accountingfinance.service.ProjectService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Response;
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
 * MilestoneController: new/edit form, create/update with validation, start/complete/reset
 * status transitions and delete, all driven from the project detail page.
 *
 * Each test creates its own project (unique code), so sequences and milestone lists are
 * exact regardless of what other test classes add to the shared database. Progress
 * calculation, overdue badges and payment-term linkage are covered by
 * ServiceMilestonePaymentTermTest.
 */
@DisplayName("Milestone Controller Tests")
@Import(ServiceTestDataInitializer.class)
class MilestoneControllerFunctionalTest extends PlaywrightTestBase {

    // From src/test/resources/testdata/service/clients.csv
    private static final String CLIENT_CODE = "MANDIRI";
    private static final DateTimeFormatter DISPLAY_DATE =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.forLanguageTag("id-ID"));

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ProjectMilestoneService milestoneService;

    @Autowired
    private ProjectMilestoneRepository milestoneRepository;

    private Project project;

    @BeforeEach
    void setupAndLogin() {
        project = createProject();
        loginAsAdmin();
    }

    @Test
    @DisplayName("Should open empty new milestone form from project detail")
    void shouldDisplayNewMilestoneForm() {
        openProjectDetail();
        page.locator("#btn-new-milestone").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/projects/" + project.getCode() + "/milestones/new", page.url());
        assertThat(page.locator("#page-title")).hasText("Milestone Baru");
        assertThat(page.locator("nav a[href='/projects/" + project.getCode() + "']")).hasText(project.getName());
        assertThat(page.locator("form:has(#btn-simpan)")).hasAttribute("action", "/projects/" + project.getCode() + "/milestones/new");
        assertThat(page.locator("#sequence")).hasValue("");
        assertThat(page.locator("text=Kosongkan untuk urutan otomatis")).hasCount(1);
        assertThat(page.locator("#name")).hasValue("");
        assertThat(page.locator("#weightPercent")).hasValue("");
        assertThat(page.locator("#targetDate")).hasValue("");
        assertThat(page.locator("#description")).hasValue("");
        assertThat(page.locator("a:text-is('Batal')")).hasAttribute("href", "/projects/" + project.getCode());
    }

    @Test
    @DisplayName("Should create milestone with all fields and show it on project detail")
    void shouldCreateMilestoneWithAllFields() {
        String name = "Analisis Kebutuhan " + shortId();
        LocalDate targetDate = LocalDate.of(2027, 3, 15);

        openNewForm();
        page.locator("#sequence").fill("3");
        page.locator("#name").fill(name);
        page.locator("#weightPercent").fill("40");
        page.locator("#targetDate").fill(targetDate.toString());
        page.locator("#description").fill("Workshop dan dokumen kebutuhan");
        submit();

        assertEquals(baseUrl() + "/projects/" + project.getCode(), page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Milestone berhasil ditambahkan");

        ProjectMilestone saved = singleMilestone();
        assertEquals(3, saved.getSequence());
        assertEquals(name, saved.getName());
        assertEquals(40, saved.getWeightPercent());
        assertEquals(targetDate, saved.getTargetDate());
        assertEquals("Workshop dan dokumen kebutuhan", saved.getDescription());
        assertEquals(MilestoneStatus.PENDING, saved.getStatus());
        assertNull(saved.getActualDate());

        Locator row = milestoneRow(saved.getId());
        assertThat(row.locator("p.font-medium")).hasText(name);
        assertThat(row).containsText("40%");
        assertThat(row).containsText("Target: " + DISPLAY_DATE.format(targetDate));
        assertThat(row.locator("span:text-is('Pending')")).hasCount(1);
        assertThat(row.locator("form[action$='/start']")).hasCount(1);
    }

    @Test
    @DisplayName("Should assign next sequence when sequence is left blank")
    void shouldAutoAssignSequenceWhenBlank() {
        createMilestone(5, "Existing " + shortId(), 20);
        String name = "Auto Sequence " + shortId();

        openNewForm();
        page.locator("#name").fill(name);
        page.locator("#weightPercent").fill("10");
        submit();

        assertThat(page.locator("[data-testid='flash-success']")).hasText("Milestone berhasil ditambahkan");
        ProjectMilestone saved = milestones().stream()
                .filter(m -> name.equals(m.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Milestone not persisted: " + name));
        assertEquals(6, saved.getSequence());
        assertThat(milestoneRow(saved.getId()).locator("span.rounded-full").first()).hasText("6");
    }

    @Test
    @DisplayName("Should reject milestone without name and persist nothing")
    void shouldRejectMilestoneWithoutName() {
        openNewForm();
        disableBrowserValidation();
        page.locator("#sequence").fill("1");
        page.locator("#weightPercent").fill("10");
        submit();

        assertThat(page.locator("#page-title")).hasText("Milestone Baru");
        assertThat(page.locator("#name + p.text-red-500")).hasText("Nama milestone wajib diisi");
        assertTrue(milestones().isEmpty(), "Milestone persisted despite blank name");
    }

    @Test
    @DisplayName("Should reject sequence below 1 and persist nothing")
    void shouldRejectZeroSequence() {
        openNewForm();
        disableBrowserValidation();
        page.locator("#sequence").fill("0");
        page.locator("#name").fill("Invalid Sequence");
        page.locator("#weightPercent").fill("10");
        submit();

        assertThat(page.locator("#page-title")).hasText("Milestone Baru");
        assertThat(sequenceError()).hasText("Urutan minimal 1");
        assertThat(page.locator("#name")).hasValue("Invalid Sequence");
        assertTrue(milestones().isEmpty(), "Milestone persisted despite sequence 0");
    }

    @Test
    @DisplayName("Should reject milestone without weight and persist nothing")
    void shouldRejectMilestoneWithoutWeight() {
        openNewForm();
        assertThat(page.locator("#weightPercent")).hasAttribute("required", "");
        disableBrowserValidation();
        page.locator("#name").fill("No Weight");
        submit();

        assertThat(page.locator("#page-title")).hasText("Milestone Baru");
        assertThat(page.locator("#weightPercent ~ p.text-red-500")).hasText("Bobot wajib diisi");
        assertThat(page.locator("#name")).hasValue("No Weight");
        assertTrue(milestones().isEmpty(), "Milestone persisted despite blank weight");
    }

    @Test
    @DisplayName("Should reject duplicate sequence on create and keep existing milestone only")
    void shouldRejectDuplicateSequenceOnCreate() {
        ProjectMilestone existing = createMilestone(1, "Kickoff " + shortId(), 10);

        openNewForm();
        page.locator("#sequence").fill("1");
        page.locator("#name").fill("Duplicate Sequence");
        page.locator("#weightPercent").fill("10");
        submit();

        assertThat(page.locator("#page-title")).hasText("Milestone Baru");
        assertThat(sequenceError()).hasText("Milestone with sequence 1 already exists");
        List<ProjectMilestone> all = milestones();
        assertEquals(1, all.size());
        assertEquals(existing.getId(), all.get(0).getId());
    }

    @Test
    @DisplayName("Should open edit form prefilled with milestone values")
    void shouldDisplayEditFormPrefilled() {
        ProjectMilestone milestone = createMilestone(2, "Desain " + shortId(), 35,
                LocalDate.of(2027, 6, 30), "Desain arsitektur");

        openProjectDetail();
        milestoneRow(milestone.getId()).locator("a[title='Edit']").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/projects/" + project.getCode() + "/milestones/" + milestone.getId() + "/edit",
                page.url());
        assertThat(page.locator("#page-title")).hasText("Edit Milestone");
        assertThat(page.locator("form:has(#btn-simpan)")).hasAttribute("action",
                "/projects/" + project.getCode() + "/milestones/" + milestone.getId());
        assertThat(page.locator("#sequence")).hasValue("2");
        assertThat(page.locator("#name")).hasValue(milestone.getName());
        assertThat(page.locator("#weightPercent")).hasValue("35");
        assertThat(page.locator("#targetDate")).hasValue("2027-06-30");
        assertThat(page.locator("#description")).hasValue("Desain arsitektur");
    }

    @Test
    @DisplayName("Should update all milestone fields")
    void shouldUpdateMilestone() {
        ProjectMilestone milestone = createMilestone(1, "Draft " + shortId(), 10);
        String newName = "Implementasi " + shortId();

        openEditForm(milestone.getId());
        page.locator("#sequence").fill("4");
        page.locator("#name").fill(newName);
        page.locator("#weightPercent").fill("50");
        page.locator("#targetDate").fill("2027-09-01");
        page.locator("#description").fill("Deskripsi baru");
        submit();

        assertEquals(baseUrl() + "/projects/" + project.getCode(), page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Milestone berhasil diperbarui");

        ProjectMilestone updated = milestoneRepository.findById(milestone.getId()).orElseThrow();
        assertEquals(4, updated.getSequence());
        assertEquals(newName, updated.getName());
        assertEquals(50, updated.getWeightPercent());
        assertEquals(LocalDate.of(2027, 9, 1), updated.getTargetDate());
        assertEquals("Deskripsi baru", updated.getDescription());
        assertEquals(MilestoneStatus.PENDING, updated.getStatus());

        Locator row = milestoneRow(milestone.getId());
        assertThat(row.locator("p.font-medium")).hasText(newName);
        assertThat(row).containsText("50%");
        assertThat(row).containsText("Target: " + DISPLAY_DATE.format(LocalDate.of(2027, 9, 1)));
    }

    @Test
    @DisplayName("Should reject update to a sequence used by another milestone")
    void shouldRejectDuplicateSequenceOnUpdate() {
        createMilestone(1, "First " + shortId(), 10);
        ProjectMilestone second = createMilestone(2, "Second " + shortId(), 10);

        openEditForm(second.getId());
        page.locator("#sequence").fill("1");
        page.locator("#name").fill("Renamed");
        submit();

        assertThat(page.locator("#page-title")).hasText("Edit Milestone");
        assertThat(sequenceError()).hasText("Milestone with sequence 1 already exists");
        assertThat(page.locator("form:has(#btn-simpan)")).hasAttribute("action",
                "/projects/" + project.getCode() + "/milestones/" + second.getId());

        ProjectMilestone unchanged = milestoneRepository.findById(second.getId()).orElseThrow();
        assertEquals(2, unchanged.getSequence());
        assertEquals(second.getName(), unchanged.getName());
    }

    @Test
    @DisplayName("Should reject update with blank sequence")
    void shouldRejectBlankSequenceOnUpdate() {
        ProjectMilestone milestone = createMilestone(3, "Keep Sequence " + shortId(), 10);

        openEditForm(milestone.getId());
        assertThat(page.locator("text=Kosongkan untuk urutan otomatis")).hasCount(0);
        page.locator("#sequence").fill("");
        page.locator("#name").fill("Renamed");
        submit();

        assertThat(page.locator("#page-title")).hasText("Edit Milestone");
        assertThat(sequenceError()).hasText("Urutan wajib diisi");

        ProjectMilestone unchanged = milestoneRepository.findById(milestone.getId()).orElseThrow();
        assertEquals(3, unchanged.getSequence());
        assertEquals(milestone.getName(), unchanged.getName());
    }

    @Test
    @DisplayName("Should start pending milestone")
    void shouldStartMilestone() {
        ProjectMilestone milestone = createMilestone(1, "Start " + shortId(), 10);

        openProjectDetail();
        milestoneRow(milestone.getId()).locator("form[action$='/start'] button").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/projects/" + project.getCode(), page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Milestone dimulai");
        Locator row = milestoneRow(milestone.getId());
        assertThat(row.locator("span:text-is('Proses')")).hasCount(1);
        assertThat(row.locator("form[action$='/start']")).hasCount(0);
        assertThat(row.locator("form[action$='/complete']")).hasCount(1);

        ProjectMilestone started = milestoneRepository.findById(milestone.getId()).orElseThrow();
        assertEquals(MilestoneStatus.IN_PROGRESS, started.getStatus());
        assertNull(started.getActualDate());
    }

    @Test
    @DisplayName("Should complete in-progress milestone and record actual date")
    void shouldCompleteMilestone() {
        ProjectMilestone milestone = createMilestone(1, "Complete " + shortId(), 10);
        milestoneService.startMilestone(milestone.getId());

        openProjectDetail();
        milestoneRow(milestone.getId()).locator("form[action$='/complete'] button").click();
        waitForPageLoad();

        assertThat(page.locator("[data-testid='flash-success']")).hasText("Milestone selesai");
        Locator row = milestoneRow(milestone.getId());
        assertThat(row.locator("span:text-is('Selesai')")).hasCount(1);
        assertThat(row).containsText("Selesai: " + DISPLAY_DATE.format(LocalDate.now()));
        assertThat(row.locator("form[action$='/complete']")).hasCount(0);
        assertThat(row.locator("form[action$='/reset']")).hasCount(1);

        ProjectMilestone completed = milestoneRepository.findById(milestone.getId()).orElseThrow();
        assertEquals(MilestoneStatus.COMPLETED, completed.getStatus());
        assertEquals(LocalDate.now(), completed.getActualDate());
    }

    @Test
    @DisplayName("Should reset completed milestone to pending and clear actual date")
    void shouldResetMilestone() {
        ProjectMilestone milestone = createMilestone(1, "Reset " + shortId(), 10);
        milestoneService.startMilestone(milestone.getId());
        milestoneService.completeMilestone(milestone.getId());

        openProjectDetail();
        milestoneRow(milestone.getId()).locator("form[action$='/reset'] button").click();
        waitForPageLoad();

        assertThat(page.locator("[data-testid='flash-success']")).hasText("Milestone direset");
        Locator row = milestoneRow(milestone.getId());
        assertThat(row.locator("span:text-is('Pending')")).hasCount(1);
        assertThat(row).not().containsText("Selesai:");
        assertThat(row.locator("form[action$='/start']")).hasCount(1);

        ProjectMilestone reset = milestoneRepository.findById(milestone.getId()).orElseThrow();
        assertEquals(MilestoneStatus.PENDING, reset.getStatus());
        assertNull(reset.getActualDate());
    }

    @Test
    @DisplayName("Should keep milestone when delete confirmation is dismissed")
    void shouldKeepMilestoneWhenDeleteDismissed() {
        ProjectMilestone milestone = createMilestone(1, "Keep " + shortId(), 10);

        openProjectDetail();
        page.onceDialog(dialog -> {
            assertEquals("Hapus milestone ini?", dialog.message());
            dialog.dismiss();
        });
        milestoneRow(milestone.getId()).locator("form[action$='/delete'] button").click();

        assertThat(milestoneRow(milestone.getId())).hasCount(1);
        assertTrue(milestoneRepository.findById(milestone.getId()).isPresent(),
                "Milestone deleted although confirmation was dismissed");
    }

    @Test
    @DisplayName("Should delete milestone after confirmation")
    void shouldDeleteMilestone() {
        ProjectMilestone milestone = createMilestone(1, "Delete " + shortId(), 10);

        openProjectDetail();
        page.onceDialog(dialog -> dialog.accept());
        milestoneRow(milestone.getId()).locator("form[action$='/delete'] button").click();
        waitForPageLoad();

        assertThat(page.locator("[data-testid='flash-success']")).hasText("Milestone berhasil dihapus");
        assertThat(milestoneRow(milestone.getId())).hasCount(0);
        assertThat(page.locator("[data-testid='milestone-section']")).containsText("Belum ada milestone");
        assertTrue(milestoneRepository.findById(milestone.getId()).isEmpty(), "Milestone still persisted");
    }

    @Test
    @DisplayName("Should return 404 for unknown milestone")
    void shouldReturn404ForUnknownMilestone() {
        Response response = page.navigate(baseUrl() + "/projects/" + project.getCode()
                + "/milestones/" + UUID.randomUUID() + "/edit");

        assertEquals(404, response.status());
    }

    // ==================== helpers ====================

    private Project createProject() {
        Project p = new Project();
        p.setCode("MS-" + shortId());
        p.setName("Proyek Milestone " + shortId());
        p.setContractValue(new BigDecimal("100000000"));
        UUID clientId = clientRepository.findByCode(CLIENT_CODE)
                .orElseThrow(() -> new IllegalStateException("Seeded client missing: " + CLIENT_CODE))
                .getId();
        return projectService.create(p, clientId);
    }

    private ProjectMilestone createMilestone(int sequence, String name, int weight) {
        return createMilestone(sequence, name, weight, null, null);
    }

    private ProjectMilestone createMilestone(int sequence, String name, int weight, LocalDate targetDate,
                                             String description) {
        ProjectMilestone m = new ProjectMilestone();
        m.setSequence(sequence);
        m.setName(name);
        m.setWeightPercent(weight);
        m.setTargetDate(targetDate);
        m.setDescription(description);
        return milestoneService.create(project.getId(), m);
    }

    private List<ProjectMilestone> milestones() {
        return milestoneRepository.findByProjectIdOrderBySequenceAsc(project.getId());
    }

    private ProjectMilestone singleMilestone() {
        List<ProjectMilestone> all = milestones();
        assertEquals(1, all.size(), "Expected exactly one milestone on " + project.getCode());
        return all.get(0);
    }

    private void openProjectDetail() {
        navigateTo("/projects/" + project.getCode());
        waitForPageLoad();
        assertThat(page.locator("[data-testid='milestone-section']")).isVisible();
    }

    private void openNewForm() {
        navigateTo("/projects/" + project.getCode() + "/milestones/new");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Milestone Baru");
    }

    private void openEditForm(UUID milestoneId) {
        navigateTo("/projects/" + project.getCode() + "/milestones/" + milestoneId + "/edit");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Edit Milestone");
    }

    private void disableBrowserValidation() {
        page.locator("#btn-simpan").evaluate("b => b.form.noValidate = true");
    }

    private void submit() {
        page.locator("#btn-simpan").click();
        waitForPageLoad();
    }

    private Locator sequenceError() {
        return page.locator("#sequence ~ p.text-red-500");
    }

    private Locator milestoneRow(UUID milestoneId) {
        return page.locator("[data-milestone-id='" + milestoneId + "']");
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
