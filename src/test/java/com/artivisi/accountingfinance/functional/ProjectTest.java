package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.page.MilestoneFormPage;
import com.artivisi.accountingfinance.functional.page.ProjectDetailPage;
import com.artivisi.accountingfinance.functional.page.ProjectFormPage;
import com.artivisi.accountingfinance.functional.page.ProjectListPage;
import com.artivisi.accountingfinance.functional.page.LoginPage;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Project Management (Section 1.9)")
class ProjectTest extends PlaywrightTestBase {

    private LoginPage loginPage;
    private ProjectListPage listPage;
    private ProjectFormPage formPage;
    private ProjectDetailPage detailPage;
    private MilestoneFormPage milestoneFormPage;

    @BeforeEach
    void setUp() {
        loginPage = new LoginPage(page, baseUrl());
        listPage = new ProjectListPage(page, baseUrl());
        formPage = new ProjectFormPage(page, baseUrl());
        detailPage = new ProjectDetailPage(page, baseUrl());
        milestoneFormPage = new MilestoneFormPage(page, baseUrl());

        loginPage.navigate().loginAsAdmin();
    }

    @Nested
    @DisplayName("1.9.5 Project List")
    class ProjectListTests {

        @Test
        @DisplayName("Should display project list page")
        void shouldDisplayProjectListPage() {
            listPage.navigate();

            listPage.assertPageTitleVisible();
            listPage.assertPageTitleText("Daftar Proyek");
        }

        @Test
        @DisplayName("Should display project table")
        void shouldDisplayProjectTable() {
            listPage.navigate();

            listPage.assertTableVisible();
        }
    }

    @Nested
    @DisplayName("1.9.6 Project Form")
    class ProjectFormTests {

        @Test
        @DisplayName("Should display new project form")
        void shouldDisplayNewProjectForm() {
            formPage.navigateToNew();

            formPage.assertPageTitleText("Proyek Baru");
        }

        @Test
        @DisplayName("Should navigate to form from list page")
        void shouldNavigateToFormFromListPage() {
            listPage.navigate();
            listPage.clickNewProjectButton();

            formPage.assertPageTitleText("Proyek Baru");
        }
    }

    @Nested
    @DisplayName("1.9.7 Project CRUD")
    class ProjectCrudTests {

        @Test
        @DisplayName("Should create new project")
        void shouldCreateNewProject() {
            formPage.navigateToNew();

            String uniqueCode = "PRJ-TEST-" + System.currentTimeMillis();
            String uniqueName = "Test Project " + System.currentTimeMillis();

            formPage.fillCode(uniqueCode);
            formPage.fillName(uniqueName);
            formPage.fillContractValue("10000000");
            formPage.clickSubmit();

            // Should redirect to detail page
            detailPage.assertProjectNameText(uniqueName);
            detailPage.assertProjectCodeText(uniqueCode);
        }

        @Test
        @DisplayName("Should show project in list after creation")
        void shouldShowProjectInListAfterCreation() {
            formPage.navigateToNew();

            String uniqueCode = "PRJ-LIST-" + System.currentTimeMillis();
            String uniqueName = "List Test Project " + System.currentTimeMillis();

            formPage.fillCode(uniqueCode);
            formPage.fillName(uniqueName);
            formPage.clickSubmit();

            // Navigate to list and search
            listPage.navigate();
            listPage.search(uniqueCode);

            assertThat(listPage.hasProjectWithName(uniqueName)).isTrue();
        }
    }

    @Nested
    @DisplayName("1.9.8 Project Status")
    class ProjectStatusTests {

        @Test
        @DisplayName("Should complete active project")
        void shouldCompleteActiveProject() {
            // Create a project first
            formPage.navigateToNew();

            String uniqueCode = "PRJ-COMP-" + System.currentTimeMillis();
            String uniqueName = "Complete Test " + System.currentTimeMillis();

            formPage.fillCode(uniqueCode);
            formPage.fillName(uniqueName);
            formPage.clickSubmit();

            // Should be active by default
            detailPage.assertStatusText("Aktif");
            assertThat(detailPage.hasCompleteButton()).isTrue();

            // Complete
            detailPage.clickCompleteButton();

            // Should show completed status
            detailPage.assertStatusText("Selesai");
            assertThat(detailPage.hasReactivateButton()).isTrue();
        }

        @Test
        @DisplayName("Should archive active project")
        void shouldArchiveActiveProject() {
            // Create a project first
            formPage.navigateToNew();

            String uniqueCode = "PRJ-ARCH-" + System.currentTimeMillis();
            String uniqueName = "Archive Test " + System.currentTimeMillis();

            formPage.fillCode(uniqueCode);
            formPage.fillName(uniqueName);
            formPage.clickSubmit();

            // Archive
            detailPage.clickArchiveButton();

            // Should show archived status
            detailPage.assertStatusText("Diarsipkan");
            assertThat(detailPage.hasReactivateButton()).isTrue();
        }

        @Test
        @DisplayName("Should reactivate completed project")
        void shouldReactivateCompletedProject() {
            // Create and complete a project
            formPage.navigateToNew();

            String uniqueCode = "PRJ-REACT-" + System.currentTimeMillis();
            String uniqueName = "Reactivate Test " + System.currentTimeMillis();

            formPage.fillCode(uniqueCode);
            formPage.fillName(uniqueName);
            formPage.clickSubmit();

            detailPage.clickCompleteButton();
            detailPage.assertStatusText("Selesai");

            // Reactivate
            detailPage.clickReactivateButton();

            // Should show active status
            detailPage.assertStatusText("Aktif");
            assertThat(detailPage.hasCompleteButton()).isTrue();
        }
    }

    @Nested
    @DisplayName("1.9.9 Milestone Management")
    class MilestoneTests {

        @Test
        @DisplayName("Should display milestone section on project detail")
        void shouldDisplayMilestoneSection() {
            // Create a project first
            formPage.navigateToNew();

            String uniqueCode = "PRJ-MS-" + System.currentTimeMillis();
            String uniqueName = "Milestone Test " + System.currentTimeMillis();

            formPage.fillCode(uniqueCode);
            formPage.fillName(uniqueName);
            formPage.clickSubmit();

            // Should have milestone section
            assertThat(detailPage.hasMilestoneSection()).isTrue();
            assertThat(detailPage.hasNewMilestoneButton()).isTrue();
        }

        @Test
        @DisplayName("Should create new milestone")
        void shouldCreateNewMilestone() {
            // Create a project first
            formPage.navigateToNew();

            String uniqueCode = "PRJ-MSCR-" + System.currentTimeMillis();
            String uniqueName = "Milestone Create Test " + System.currentTimeMillis();

            formPage.fillCode(uniqueCode);
            formPage.fillName(uniqueName);
            formPage.clickSubmit();

            // Click new milestone button
            detailPage.clickNewMilestoneButton();

            // Fill milestone form
            milestoneFormPage.assertPageTitleText("Milestone Baru");
            String milestoneName = "Design Phase";
            milestoneFormPage.fillName(milestoneName);
            milestoneFormPage.fillWeight("25");
            milestoneFormPage.clickSubmit();

            // Should show milestone in project detail
            assertThat(detailPage.hasMilestoneWithName(milestoneName)).isTrue();
        }

        @Test
        @DisplayName("Should change milestone status from pending to in_progress")
        void shouldStartMilestone() {
            // Create a project with milestone
            formPage.navigateToNew();

            String uniqueCode = "PRJ-MSST-" + System.currentTimeMillis();
            String uniqueName = "Milestone Start Test " + System.currentTimeMillis();

            formPage.fillCode(uniqueCode);
            formPage.fillName(uniqueName);
            formPage.clickSubmit();

            // Create milestone
            detailPage.clickNewMilestoneButton();
            String milestoneName = "Implementation Phase";
            milestoneFormPage.fillName(milestoneName);
            milestoneFormPage.clickSubmit();

            // Start milestone
            detailPage.clickMilestoneStartButton(milestoneName);

            // Should show in progress status
            assertThat(detailPage.getMilestoneStatus(milestoneName)).isEqualTo("Proses");
        }

        @Test
        @DisplayName("Should complete in-progress milestone")
        void shouldCompleteMilestone() {
            // Create a project with milestone
            formPage.navigateToNew();

            String uniqueCode = "PRJ-MSCP-" + System.currentTimeMillis();
            String uniqueName = "Milestone Complete Test " + System.currentTimeMillis();

            formPage.fillCode(uniqueCode);
            formPage.fillName(uniqueName);
            formPage.clickSubmit();

            // Create milestone
            detailPage.clickNewMilestoneButton();
            String milestoneName = "Testing Phase";
            milestoneFormPage.fillName(milestoneName);
            milestoneFormPage.clickSubmit();

            // Start then complete
            detailPage.clickMilestoneStartButton(milestoneName);
            detailPage.clickMilestoneCompleteButton(milestoneName);

            // Should show completed status
            assertThat(detailPage.getMilestoneStatus(milestoneName)).isEqualTo("Selesai");
        }

        @Test
        @DisplayName("Should delete milestone")
        void shouldDeleteMilestone() {
            // Create a project with milestone
            formPage.navigateToNew();

            String uniqueCode = "PRJ-MSDL-" + System.currentTimeMillis();
            String uniqueName = "Milestone Delete Test " + System.currentTimeMillis();

            formPage.fillCode(uniqueCode);
            formPage.fillName(uniqueName);
            formPage.clickSubmit();

            // Create milestone
            detailPage.clickNewMilestoneButton();
            String milestoneName = "Deployment Phase";
            milestoneFormPage.fillName(milestoneName);
            milestoneFormPage.clickSubmit();

            int countBefore = detailPage.getMilestoneCount();

            // Delete milestone
            detailPage.clickMilestoneDeleteButton(milestoneName);

            // Should be removed
            assertThat(detailPage.getMilestoneCount()).isEqualTo(countBefore - 1);
        }
    }
}
