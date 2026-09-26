package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.Bill;
import com.artivisi.accountingfinance.entity.BillPayment;
import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.entity.InvoicePayment;
import com.artivisi.accountingfinance.enums.BillStatus;
import com.artivisi.accountingfinance.enums.InvoiceStatus;
import com.artivisi.accountingfinance.enums.PaymentMethod;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.ProductRepository;
import com.artivisi.accountingfinance.service.BillService;
import com.artivisi.accountingfinance.service.ClientService;
import com.artivisi.accountingfinance.service.InvoiceService;
import com.artivisi.accountingfinance.service.VendorService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Payment recording on invoices (receivables) and vendor bills (payables): the detail-page payment
 * form, partial and full payments with status, paid amount, balance due and payment history, and the
 * refusals for overpayment and for documents not yet sent/approved.
 *
 * Invoices and bills are created per test for a fresh client/vendor, dated in 2087.
 */
@DisplayName("Payment Tracking - Functional Tests")
@Import(ServiceTestDataInitializer.class)
class PaymentTrackingTest extends PlaywrightTestBase {

    private static final int YEAR = 2087;

    @Autowired private InvoiceService invoiceService;
    @Autowired private BillService billService;
    @Autowired private ClientService clientService;
    @Autowired private VendorService vendorService;
    @Autowired private ProductRepository productRepository;
    @Autowired private ChartOfAccountRepository chartOfAccountRepository;

    private ArApFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new ArApFixture(clientService, vendorService, invoiceService, billService,
                productRepository, chartOfAccountRepository);
        loginAsAdmin();
    }

    @Test
    @DisplayName("Sent invoice shows balance due and a collapsed payment form capped at the balance")
    void shouldShowPaymentFormForSentInvoice() {
        Invoice invoice = sentInvoice();

        navigateTo("/invoices/" + invoice.getInvoiceNumber());
        waitForPageLoad();

        assertThat(page.getByTestId("invoice-status")).hasText("Terkirim");
        assertThat(infoValue("invoice-detail", "Total")).hasText("Rp 11,100,000");
        assertThat(page.getByTestId("balance-due")).hasText("Rp 11,100,000");
        assertThat(infoValue("invoice-detail", "Sudah Dibayar")).hasCount(0);
        assertThat(page.getByTestId("payment-history")).hasCount(0);

        Locator toggle = page.getByTestId("toggle-payment-form");
        assertThat(toggle).hasText("Catat Pembayaran");
        assertThat(page.locator("#paymentDate")).isHidden();
        toggle.click();

        assertThat(toggle).hasText("Tutup");
        assertThat(page.locator("#paymentDate")).isVisible();
        assertThat(page.locator("#amount")).hasAttribute("max", "11100000.00");
        assertThat(page.locator("#paymentMethod option")).hasText(new String[] {
                "Transfer Bank", "Tunai", "Cek/Giro", "Kartu Kredit", "E-Wallet", "Lainnya"});
        assertThat(page.getByTestId("submit-payment")).isVisible();
    }

    @Test
    @DisplayName("Partial invoice payment: Sebagian, paid amount, balance due, history row and persisted payment")
    void shouldRecordPartialPaymentOnInvoice() {
        Invoice invoice = sentInvoice();

        navigateTo("/invoices/" + invoice.getInvoiceNumber());
        waitForPageLoad();
        submitPaymentForm(YEAR + "-02-15", "3000000", "CASH", "KW-001", "Termin pertama");

        assertThat(page).hasURL(baseUrl() + "/invoices/" + invoice.getInvoiceNumber());
        assertThat(page.getByTestId("flash-success")).hasText("Pembayaran berhasil dicatat");
        assertThat(page.getByTestId("invoice-status")).hasText("Sebagian");
        assertThat(infoValue("invoice-detail", "Sudah Dibayar")).hasText("Rp 3,000,000");
        assertThat(page.getByTestId("balance-due")).hasText("Rp 8,100,000");
        assertThat(page.locator("#amount")).hasAttribute("max", "8100000.00");
        assertThat(historyRows()).hasCount(1);
        assertThat(historyRows().first().locator("td")).hasText(new String[] {
                "15 Feb " + YEAR, "Tunai", "KW-001", "Rp 3,000,000"});

        Invoice saved = invoiceService.findById(invoice.getId());
        assertThat(saved.getStatus()).isEqualTo(InvoiceStatus.PARTIAL);
        assertThat(saved.getPaidAt()).isNull();
        List<InvoicePayment> payments = invoiceService.findPaymentsByInvoiceId(invoice.getId());
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).getPaymentDate()).isEqualTo(LocalDate.of(YEAR, 2, 15));
        assertThat(payments.get(0).getAmount()).isEqualByComparingTo("3000000");
        assertThat(payments.get(0).getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(payments.get(0).getReferenceNumber()).isEqualTo("KW-001");
        assertThat(payments.get(0).getNotes()).isEqualTo("Termin pertama");
    }

    @Test
    @DisplayName("Second payment settling the balance marks the invoice Lunas and removes the payment form")
    void shouldSettleInvoiceWithSecondPayment() {
        Invoice invoice = sentInvoice();
        fixture.payInvoice(invoice, LocalDate.of(YEAR, 2, 10), "4000000", "TRF-1");

        navigateTo("/invoices/" + invoice.getInvoiceNumber());
        waitForPageLoad();
        assertThat(page.getByTestId("balance-due")).hasText("Rp 7,100,000");
        submitPaymentForm(YEAR + "-02-20", "7100000", "TRANSFER", "TRF-2", "");

        assertThat(page.getByTestId("flash-success")).hasText("Pembayaran berhasil dicatat");
        assertThat(page.getByTestId("invoice-status")).hasText("Lunas");
        assertThat(infoValue("invoice-detail", "Sudah Dibayar")).hasText("Rp 11,100,000");
        assertThat(page.getByTestId("balance-due")).hasCount(0);
        assertThat(page.getByTestId("payment-form-section")).hasCount(0);
        assertThat(historyRows()).hasCount(2);
        assertThat(historyRows().nth(0).locator("td")).hasText(new String[] {
                "10 Feb " + YEAR, "Transfer Bank", "TRF-1", "Rp 4,000,000"});
        assertThat(historyRows().nth(1).locator("td")).hasText(new String[] {
                "20 Feb " + YEAR, "Transfer Bank", "TRF-2", "Rp 7,100,000"});

        Invoice saved = invoiceService.findById(invoice.getId());
        assertThat(saved.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(saved.getPaidAt()).isNotNull();
    }

    @Test
    @DisplayName("Invoice overpayment and payment on a draft invoice are refused with the reason; nothing recorded")
    void shouldRefuseInvalidInvoicePayments() {
        Invoice invoice = sentInvoice();
        fixture.payInvoice(invoice, LocalDate.of(YEAR, 2, 10), "10000000", "TRF-1");

        navigateTo("/invoices/" + invoice.getInvoiceNumber());
        waitForPageLoad();
        postPayment("/invoices/" + invoice.getInvoiceNumber() + "/payments", "1100001");
        navigateTo("/invoices/" + invoice.getInvoiceNumber());
        waitForPageLoad();

        assertThat(page.getByTestId("flash-error"))
                .hasText("Total pembayaran (Rp 11100001.00) melebihi total invoice (Rp 11100000.00)");
        assertThat(page.getByTestId("invoice-status")).hasText("Sebagian");
        assertThat(page.getByTestId("balance-due")).hasText("Rp 1,100,000");
        assertThat(invoiceService.findPaymentsByInvoiceId(invoice.getId())).hasSize(1);

        Invoice draft = fixture.draftInvoice(fixture.client("Klien Draf"), LocalDate.of(YEAR, 3, 1),
                LocalDate.of(YEAR, 3, 31), "500000", null);
        navigateTo("/invoices/" + draft.getInvoiceNumber());
        waitForPageLoad();
        assertThat(page.getByTestId("payment-form-section")).hasCount(0);
        postPayment("/invoices/" + draft.getInvoiceNumber() + "/payments", "500000");
        navigateTo("/invoices/" + draft.getInvoiceNumber());
        waitForPageLoad();

        assertThat(page.getByTestId("flash-error")).hasText(
                "Pembayaran hanya bisa dicatat untuk invoice berstatus Terkirim, Jatuh Tempo, atau Sebagian");
        assertThat(invoiceService.findPaymentsByInvoiceId(draft.getId())).isEmpty();
        assertThat(invoiceService.findById(draft.getId()).getStatus()).isEqualTo(InvoiceStatus.DRAFT);
    }

    @Test
    @DisplayName("Approved bill: partial payment -> Sebagian with balance, second payment -> Lunas; payments persisted")
    void shouldRecordPaymentsOnBill() {
        Bill bill = fixture.approvedBill(fixture.vendor("Vendor Bayar"), LocalDate.of(YEAR, 2, 5),
                LocalDate.of(YEAR, 3, 5), "5000000", "11");

        navigateTo("/bills/" + bill.getBillNumber());
        waitForPageLoad();
        assertThat(page.getByTestId("balance-due")).hasText("Rp 5,550,000");
        assertThat(page.locator("#paymentDate")).isHidden();
        page.getByTestId("toggle-payment-form").click();
        assertThat(page.locator("#amount")).hasAttribute("max", "5550000.00");
        submitPaymentForm(YEAR + "-02-25", "2000000", "TRANSFER", "TRF-BILL-1", "DP");

        assertThat(page.getByTestId("flash-success")).hasText("Pembayaran berhasil dicatat");
        assertThat(page.getByTestId("bill-status")).hasText("Sebagian");
        assertThat(infoValue("bill-detail", "Sudah Dibayar")).hasText("Rp 2,000,000");
        assertThat(page.getByTestId("balance-due")).hasText("Rp 3,550,000");
        assertThat(historyRows()).hasCount(1);
        assertThat(historyRows().first().locator("td")).hasText(new String[] {
                "25 Feb " + YEAR, "Transfer Bank", "TRF-BILL-1", "Rp 2,000,000"});
        assertThat(billService.findById(bill.getId()).getStatus()).isEqualTo(BillStatus.PARTIAL);

        submitPaymentForm(YEAR + "-03-05", "3550000", "CHECK", "GIRO-7", "");

        assertThat(page.getByTestId("bill-status")).hasText("Lunas");
        assertThat(page.getByTestId("balance-due")).hasCount(0);
        assertThat(page.getByTestId("payment-form-section")).hasCount(0);
        assertThat(historyRows()).hasCount(2);
        assertThat(historyRows().nth(1).locator("td")).hasText(new String[] {
                "05 Mar " + YEAR, "Cek/Giro", "GIRO-7", "Rp 3,550,000"});

        Bill paid = billService.findById(bill.getId());
        assertThat(paid.getStatus()).isEqualTo(BillStatus.PAID);
        assertThat(paid.getPaidAt()).isNotNull();
        List<BillPayment> payments = billService.findPaymentsByBillId(bill.getId());
        assertThat(payments).extracting(p -> p.getPaymentDate() + "|" + p.getAmount().toPlainString() + "|"
                        + p.getPaymentMethod() + "|" + p.getReferenceNumber() + "|" + p.getNotes())
                .containsExactly(YEAR + "-02-25|2000000.00|TRANSFER|TRF-BILL-1|DP",
                        YEAR + "-03-05|3550000.00|CHECK|GIRO-7|");
    }

    @Test
    @DisplayName("Bill overpayment and payment on a draft bill are refused with the reason; nothing recorded")
    void shouldRefuseInvalidBillPayments() {
        Bill bill = fixture.approvedBill(fixture.vendor("Vendor Lebih"), LocalDate.of(YEAR, 4, 1),
                LocalDate.of(YEAR, 5, 1), "1000000", null);

        navigateTo("/bills/" + bill.getBillNumber());
        waitForPageLoad();
        postPayment("/bills/" + bill.getBillNumber() + "/payments", "1000001");
        navigateTo("/bills/" + bill.getBillNumber());
        waitForPageLoad();

        assertThat(page.getByTestId("flash-error"))
                .hasText("Total pembayaran (Rp 1000001) melebihi total tagihan (Rp 1000000.00)");
        assertThat(page.getByTestId("bill-status")).hasText("Disetujui");
        assertThat(billService.findPaymentsByBillId(bill.getId())).isEmpty();

        Bill draft = fixture.draftBill(fixture.vendor("Vendor Draf"), LocalDate.of(YEAR, 4, 2),
                LocalDate.of(YEAR, 5, 2), "200000", null);
        navigateTo("/bills/" + draft.getBillNumber());
        waitForPageLoad();
        assertThat(page.getByTestId("payment-form-section")).hasCount(0);
        postPayment("/bills/" + draft.getBillNumber() + "/payments", "200000");
        navigateTo("/bills/" + draft.getBillNumber());
        waitForPageLoad();

        assertThat(page.getByTestId("flash-error")).hasText(
                "Pembayaran hanya bisa dicatat untuk tagihan berstatus Disetujui, Jatuh Tempo, atau Sebagian");
        assertThat(billService.findPaymentsByBillId(draft.getId())).isEmpty();
        assertThat(billService.findById(draft.getId()).getStatus()).isEqualTo(BillStatus.DRAFT);
    }

    private Invoice sentInvoice() {
        return fixture.sentInvoice(fixture.client("Klien Bayar"), LocalDate.of(YEAR, 2, 1),
                LocalDate.of(YEAR, 3, 1), "10000000", "11");
    }

    private void submitPaymentForm(String date, String amount, String method, String reference, String notes) {
        Locator toggle = page.getByTestId("toggle-payment-form");
        if ("Catat Pembayaran".equals(toggle.textContent().strip())) {
            toggle.click();
        }
        page.locator("#paymentDate").fill(date);
        page.locator("#amount").fill(amount);
        page.locator("#paymentMethod").selectOption(method);
        page.locator("#referenceNumber").fill(reference);
        page.locator("#paymentNotes").fill(notes);
        page.getByTestId("submit-payment").click();
        waitForPageLoad();
    }

    /** Posts a payment the browser form would block (max/state), as a crafted request would. */
    private void postPayment(String path, String amount) {
        String csrf = page.locator("meta[name='_csrf']").getAttribute("content");
        page.request().post(baseUrl() + path, RequestOptions.create()
                .setHeader("X-CSRF-TOKEN", csrf)
                .setMaxRedirects(0)
                .setForm(FormData.create()
                        .set("paymentDate", YEAR + "-03-10")
                        .set("amount", amount)
                        .set("paymentMethod", "TRANSFER")));
    }

    private Locator historyRows() {
        return page.locator("[data-testid='payment-history'] tbody tr");
    }

    private Locator infoValue(String section, String label) {
        return page.locator("[data-testid='" + section + "'] div:has(> p.text-gray-500:text-is('" + label + "')) > p.font-medium");
    }
}
