package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.page.ClientFormPage;
import com.artivisi.accountingfinance.functional.page.InvoiceDetailPage;
import com.artivisi.accountingfinance.functional.page.InvoiceListPage;
import com.artivisi.accountingfinance.functional.page.MilestoneFormPage;
import com.artivisi.accountingfinance.functional.page.PaymentTermFormPage;
import com.artivisi.accountingfinance.functional.page.ProjectDetailPage;
import com.artivisi.accountingfinance.functional.page.ProjectFormPage;
import com.artivisi.accountingfinance.functional.page.ProjectListPage;
import com.artivisi.accountingfinance.functional.page.LoginPage;
import com.artivisi.accountingfinance.functional.page.TransactionFormPage;
import com.artivisi.accountingfinance.functional.page.TransactionListPage;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Project Management (Section 1.9)")
class ProjectTest extends PlaywrightTestBase {

    private LoginPage loginPage;
    private ProjectListPage listPage;
    private ProjectFormPage formPage;
    private ProjectDetailPage detailPage;
    private MilestoneFormPage milestoneFormPage;
    private PaymentTermFormPage paymentTermFormPage;
    private ClientFormPage clientFormPage;
    private InvoiceListPage invoiceListPage;
    private InvoiceDetailPage invoiceDetailPage;
    private TransactionListPage transactionListPage;
    private TransactionFormPage transactionFormPage;

    @BeforeEach
    void setUp() {
        loginPage = new LoginPage(page, baseUrl());
        listPage = new ProjectListPage(page, baseUrl());
        formPage = new ProjectFormPage(page, baseUrl());
        detailPage = new ProjectDetailPage(page, baseUrl());
        milestoneFormPage = new MilestoneFormPage(page, baseUrl());
        paymentTermFormPage = new PaymentTermFormPage(page, baseUrl());
        clientFormPage = new ClientFormPage(page, baseUrl());
        invoiceListPage = new InvoiceListPage(page, baseUrl());
        invoiceDetailPage = new InvoiceDetailPage(page, baseUrl());
        transactionListPage = new TransactionListPage(page, baseUrl());
        transactionFormPage = new TransactionFormPage(page, baseUrl());

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

    @Nested
    @DisplayName("1.9.14 Payment Term Invoice Generation")
    class PaymentTermInvoiceTests {

        private String createTestClientAndGetProjectId() {
            // Create client first
            clientFormPage.navigateToNew();
            String clientCode = "CLI-PT-" + System.currentTimeMillis();
            String clientName = "Payment Term Test Client " + System.currentTimeMillis();
            clientFormPage.fillCode(clientCode);
            clientFormPage.fillName(clientName);
            clientFormPage.clickSubmit();

            // Create project with client
            formPage.navigateToNew();
            String uniqueCode = "PRJ-PT-" + System.currentTimeMillis();
            String uniqueName = "Payment Term Test " + System.currentTimeMillis();
            formPage.fillCode(uniqueCode);
            formPage.fillName(uniqueName);
            formPage.fillContractValue("10000000");
            formPage.selectClientByIndex(1); // Select first client
            formPage.clickSubmit();

            // Extract project ID from current URL
            String url = page.url();
            return url.substring(url.lastIndexOf("/") + 1);
        }

        @Test
        @DisplayName("Should display payment terms section")
        void shouldDisplayPaymentTermsSection() {
            createTestClientAndGetProjectId();

            assertThat(detailPage.hasPaymentTermsSection()).isTrue();
            assertThat(detailPage.hasNewPaymentTermButton()).isTrue();
        }

        @Test
        @DisplayName("Should create payment term")
        void shouldCreatePaymentTerm() {
            createTestClientAndGetProjectId();

            // Click new payment term button
            detailPage.clickNewPaymentTermButton();

            // Fill payment term form
            paymentTermFormPage.assertPageTitleText("Termin Pembayaran Baru");
            String termName = "DP 30%";
            paymentTermFormPage.fillName(termName);
            paymentTermFormPage.selectDueTrigger("ON_SIGNING");
            paymentTermFormPage.fillPercentage("30");
            paymentTermFormPage.clickSubmit();

            // Wait for redirect to project detail
            page.waitForURL("**/projects/**");
            page.waitForLoadState();

            // Should show payment term in project detail
            assertThat(detailPage.hasPaymentTermWithName(termName)).isTrue();
        }

        @Test
        @DisplayName("Should generate invoice from payment term")
        void shouldGenerateInvoiceFromPaymentTerm() {
            String projectId = createTestClientAndGetProjectId();

            // Create payment term
            detailPage.clickNewPaymentTermButton();
            String termName = "Invoice Gen Test " + System.currentTimeMillis();
            paymentTermFormPage.fillName(termName);
            paymentTermFormPage.selectDueTrigger("ON_SIGNING");
            paymentTermFormPage.fillPercentage("50");
            paymentTermFormPage.clickSubmit();

            // Wait for redirect to project detail
            page.waitForURL("**/projects/" + projectId);
            page.waitForLoadState();

            // Verify payment term was created
            assertThat(detailPage.hasPaymentTermWithName(termName)).isTrue();

            // Click generate invoice button
            detailPage.clickGenerateInvoiceButton(termName);

            // Should redirect to invoice detail page (now controller redirects to invoice)
            page.waitForURL("**/invoices/**");
            page.waitForLoadState();
            assertThat(page.url()).contains("/invoices/");
        }
    }

    @Nested
    @DisplayName("1.9.15 Invoice Payment Flow")
    class InvoicePaymentFlowTests {

        @Test
        @DisplayName("Should complete full invoice payment flow with transaction")
        void shouldCompleteInvoicePaymentFlowWithTransaction() {
            // Create client
            clientFormPage.navigateToNew();
            String clientCode = "CLI-PAY-" + System.currentTimeMillis();
            clientFormPage.fillCode(clientCode);
            clientFormPage.fillName("Payment Flow Test Client");
            clientFormPage.clickSubmit();

            // Create project with client
            formPage.navigateToNew();
            String projectCode = "PRJ-PAY-" + System.currentTimeMillis();
            formPage.fillCode(projectCode);
            formPage.fillName("Payment Flow Test Project");
            formPage.fillContractValue("5000000");
            formPage.selectClientByIndex(1);
            formPage.clickSubmit();

            // Extract project ID from URL
            String url = page.url();
            String projectId = url.substring(url.lastIndexOf("/") + 1);

            // Create payment term
            detailPage.clickNewPaymentTermButton();
            String termName = "Full Payment Test";
            paymentTermFormPage.fillName(termName);
            paymentTermFormPage.selectDueTrigger("ON_SIGNING");
            paymentTermFormPage.fillPercentage("100");
            paymentTermFormPage.clickSubmit();

            // Wait for redirect to project detail
            page.waitForURL("**/projects/" + projectId);
            page.waitForLoadState();

            // Verify payment term was created
            assertThat(detailPage.hasPaymentTermWithName(termName)).isTrue();

            // Generate invoice from payment term
            detailPage.clickGenerateInvoiceButton(termName);

            // Wait for redirect to invoice detail page
            page.waitForURL("**/invoices/**");
            page.waitForLoadState();

            // Invoice should be created - now send it
            invoiceDetailPage.assertStatusText("Draf");
            invoiceDetailPage.clickSendButton();
            invoiceDetailPage.assertStatusText("Terkirim");

            // Click "Tandai Lunas" - redirects to transaction form
            invoiceDetailPage.clickMarkPaidLink();

            // Wait for transaction form
            page.waitForURL("**/transactions/new**");
            page.waitForLoadState();

            // Should be on transaction form with invoice context
            assertThat(page.url()).contains("/transactions/new");
            assertThat(page.url()).contains("invoiceId=");

            // Fill transaction form - amount and description should be pre-filled but fill them to be safe
            String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
            transactionFormPage.fillTransactionDate(today);
            transactionFormPage.fillAmount("5000000");
            transactionFormPage.fillDescription("Pembayaran invoice test");
            transactionFormPage.fillReferenceNumber("TRF-" + System.currentTimeMillis());

            // Save and post transaction
            transactionFormPage.clickSaveAndPost();
            page.waitForLoadState();

            // Transaction should be created, navigate to invoice to verify PAID status
            invoiceListPage.navigate();
            // The latest invoice should now be PAID
            assertThat(page.content()).contains("Lunas");
        }
    }

    @Nested
    @DisplayName("1.9.16 Transaction Project Filter")
    class TransactionProjectFilterTests {

        @Test
        @DisplayName("Should display project filter on transaction list")
        void shouldDisplayProjectFilterOnTransactionList() {
            // Create a project first so the filter is visible
            formPage.navigateToNew();
            String projectCode = "PRJ-FILT-DISP-" + System.currentTimeMillis();
            formPage.fillCode(projectCode);
            formPage.fillName("Filter Display Test");
            formPage.clickSubmit();

            transactionListPage.navigate();

            // Project filter should be visible when projects exist
            assertThat(transactionListPage.hasProjectFilter()).isTrue();
        }

        @Test
        @DisplayName("Should filter transactions by project")
        void shouldFilterTransactionsByProject() {
            // Create a project
            formPage.navigateToNew();
            String projectCode = "PRJ-FILT-" + System.currentTimeMillis();
            String projectName = "Filter Test Project";
            formPage.fillCode(projectCode);
            formPage.fillName(projectName);
            formPage.clickSubmit();

            // Verify URL now contains the project code
            String url = page.url();
            assertThat(url).endsWith("/" + projectCode);

            // Navigate to transactions and filter by this project
            transactionListPage.navigate();
            page.waitForLoadState();

            // Filter by the new project - this will wait for URL change
            transactionListPage.filterByProject(projectCode);

            // The filter should be applied (URL should contain projectCode parameter)
            assertThat(page.url()).contains("projectCode=" + projectCode);
        }
    }
}
