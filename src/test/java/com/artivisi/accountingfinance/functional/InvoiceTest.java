package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.functional.page.ClientFormPage;
import com.artivisi.accountingfinance.functional.page.InvoiceDetailPage;
import com.artivisi.accountingfinance.functional.page.InvoiceFormPage;
import com.artivisi.accountingfinance.functional.page.InvoiceListPage;
import com.artivisi.accountingfinance.functional.page.LoginPage;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Invoice Management (Section 1.9)")
class InvoiceTest extends PlaywrightTestBase {

    private LoginPage loginPage;
    private InvoiceListPage listPage;
    private InvoiceFormPage formPage;
    private InvoiceDetailPage detailPage;
    private ClientFormPage clientFormPage;

    @BeforeEach
    void setUp() {
        loginPage = new LoginPage(page, baseUrl());
        listPage = new InvoiceListPage(page, baseUrl());
        formPage = new InvoiceFormPage(page, baseUrl());
        detailPage = new InvoiceDetailPage(page, baseUrl());
        clientFormPage = new ClientFormPage(page, baseUrl());

        loginPage.navigate().loginAsAdmin();
    }

    @Nested
    @DisplayName("1.9.10 Invoice List")
    class InvoiceListTests {

        @Test
        @DisplayName("Should display invoice list page")
        void shouldDisplayInvoiceListPage() {
            listPage.navigate();

            listPage.assertPageTitleVisible();
            listPage.assertPageTitleText("Daftar Invoice");
        }

        @Test
        @DisplayName("Should display invoice table")
        void shouldDisplayInvoiceTable() {
            listPage.navigate();

            listPage.assertTableVisible();
        }
    }

    @Nested
    @DisplayName("1.9.11 Invoice Form")
    class InvoiceFormTests {

        @Test
        @DisplayName("Should display new invoice form")
        void shouldDisplayNewInvoiceForm() {
            formPage.navigateToNew();

            formPage.assertPageTitleText("Invoice Baru");
        }

        @Test
        @DisplayName("Should navigate to form from list page")
        void shouldNavigateToFormFromListPage() {
            listPage.navigate();
            listPage.clickNewInvoiceButton();

            formPage.assertPageTitleText("Invoice Baru");
        }
    }

    @Nested
    @DisplayName("1.9.12 Invoice CRUD")
    class InvoiceCrudTests {

        private void createTestClient() {
            clientFormPage.navigateToNew();
            String uniqueCode = "CLI-INV-" + System.currentTimeMillis();
            String uniqueName = "Invoice Test Client " + System.currentTimeMillis();
            clientFormPage.fillCode(uniqueCode);
            clientFormPage.fillName(uniqueName);
            clientFormPage.clickSubmit();
        }

        @Test
        @DisplayName("Should create new invoice")
        void shouldCreateNewInvoice() {
            // Create client first
            createTestClient();

            // Navigate to new invoice form
            formPage.navigateToNew();

            String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
            String dueDate = LocalDate.now().plusDays(30).format(DateTimeFormatter.ISO_DATE);

            // Select first available client (index 1, as 0 is "-- Pilih Klien --")
            formPage.selectClientByIndex(1);
            formPage.fillInvoiceDate(today);
            formPage.fillDueDate(dueDate);
            formPage.fillAmount("10000000");
            formPage.fillNotes("Test invoice");
            formPage.clickSubmit();

            // Should redirect to detail page with auto-generated invoice number
            detailPage.assertPageTitleVisible();
            assertThat(detailPage.hasSendButton()).isTrue();
        }
    }

    @Nested
    @DisplayName("1.9.13 Invoice Status")
    class InvoiceStatusTests {

        private void createTestClient() {
            clientFormPage.navigateToNew();
            String uniqueCode = "CLI-STS-" + System.currentTimeMillis();
            String uniqueName = "Status Test Client " + System.currentTimeMillis();
            clientFormPage.fillCode(uniqueCode);
            clientFormPage.fillName(uniqueName);
            clientFormPage.clickSubmit();
        }

        private void createTestInvoice() {
            formPage.navigateToNew();
            String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
            String dueDate = LocalDate.now().plusDays(30).format(DateTimeFormatter.ISO_DATE);

            formPage.selectClientByIndex(1);
            formPage.fillInvoiceDate(today);
            formPage.fillDueDate(dueDate);
            formPage.fillAmount("5000000");
            formPage.clickSubmit();
        }

        @Test
        @DisplayName("Should send draft invoice")
        void shouldSendDraftInvoice() {
            createTestClient();
            createTestInvoice();

            // Should be draft by default
            detailPage.assertStatusText("Draf");
            assertThat(detailPage.hasSendButton()).isTrue();

            // Send the invoice
            detailPage.clickSendButton();

            // Should show sent status
            detailPage.assertStatusText("Terkirim");
            assertThat(detailPage.hasMarkPaidLink()).isTrue();
        }

        @Test
        @DisplayName("Should redirect to transaction form when clicking Tandai Lunas")
        void shouldRedirectToTransactionFormWhenMarkingPaid() {
            createTestClient();
            createTestInvoice();

            // Send first
            detailPage.clickSendButton();
            detailPage.assertStatusText("Terkirim");

            // Click "Tandai Lunas" - should redirect to transaction form
            detailPage.clickMarkPaidLink();

            // Should be on transaction form with template selected
            page.waitForLoadState();
            assertThat(page.url()).contains("/transactions/new");
            assertThat(page.url()).contains("invoiceId=");
            assertThat(page.url()).contains("templateId=");

            // Should see invoice payment info banner
            assertThat(page.locator("text=Pembayaran Invoice").isVisible()).isTrue();
        }

        @Test
        @DisplayName("Should cancel draft invoice")
        void shouldCancelDraftInvoice() {
            createTestClient();
            createTestInvoice();

            // Cancel
            detailPage.clickCancelButton();

            // Should show cancelled status
            detailPage.assertStatusText("Dibatalkan");
        }
    }
}
