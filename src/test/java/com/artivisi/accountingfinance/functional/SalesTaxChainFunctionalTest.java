package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.Client;
import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.ProductRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.service.BillService;
import com.artivisi.accountingfinance.service.ClientService;
import com.artivisi.accountingfinance.service.InvoiceService;
import com.artivisi.accountingfinance.service.TransactionService;
import com.artivisi.accountingfinance.service.VendorService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** Payment, faktur, bupot and report through the web UI on the IT service seed pack. Reserved test year 2154. */
@DisplayName("Sales tax chain UI")
@Import(ServiceTestDataInitializer.class)
class SalesTaxChainFunctionalTest extends PlaywrightTestBase {

    private static final int YEAR = 2154;

    @Autowired private ClientService clientService;
    @Autowired private VendorService vendorService;
    @Autowired private InvoiceService invoiceService;
    @Autowired private BillService billService;
    @Autowired private ProductRepository productRepository;
    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TransactionService transactionService;

    private ArApFixture fixture;

    @BeforeEach
    void setUp() {
        loginAsAdmin();
        fixture = new ArApFixture(clientService, vendorService, invoiceService, billService, productRepository, accountRepository);
    }

    @Test
    @DisplayName("Paid invoice shows its exceptions; recording faktur and bupot clears them; the report reconciles month by month")
    void fullFlow() {
        Client client = fixture.client("Pelanggan UI");
        Invoice invoice = fixture.sentInvoice(client, LocalDate.of(YEAR, 2, 5), LocalDate.of(YEAR, 3, 5), "10000000", "11");
        postRevenue(invoice);
        String detailPath = "/invoices/" + invoice.getInvoiceNumber();

        navigateTo(detailPath);
        waitForPageLoad();
        assertThat(page.locator("[data-testid='chain-no-faktur']")).isVisible();
        assertThat(page.locator("[data-testid='chain-booked-revenue']")).hasText("Pendapatan terposting: Rp 10.000.000");

        page.locator("[data-testid='toggle-payment-form']").click();
        page.locator("#paymentDate").fill(YEAR + "-02-20");
        page.locator("#amount").fill("11100000");
        page.locator("#pph23Withheld").fill("200000");
        page.locator("[data-testid='submit-payment']").click();
        waitForPageLoad();

        Locator exceptions = page.locator("[data-testid='chain-exceptions']");
        assertThat(exceptions.locator("[data-type='PAID_NO_FAKTUR']")).hasText("Dibayar tanpa faktur: Rp 11.100.000");
        assertThat(exceptions.locator("[data-type='MISSING_BUPOT']")).hasText("Bukti potong belum ada: Rp 200.000");

        navigateTo("/sales-tax-chain?masa=" + YEAR + "-02");
        waitForPageLoad();
        assertThat(page.locator("#recon-revenue")).hasText("10.000.000");
        assertThat(page.locator("#recon-line-REVENUE_NO_FAKTUR [data-col='amount']")).hasText("-10.000.000");
        assertThat(page.locator("#recon-dpp")).hasText("0");
        Locator row = exceptionRows(invoice);
        assertThat(row).hasCount(2);
        assertThat(row.locator("[data-col='amount']").first()).hasText("11.100.000");

        navigateTo(detailPath);
        waitForPageLoad();
        String fakturNumber = "F" + suffix();
        page.locator("#toggle-faktur-form").click();
        assertThat(page.locator("#dpp")).hasValue("10000000.00");
        assertThat(page.locator("#ppn")).hasValue("1100000.00");
        page.locator("#fakturNumber").fill(fakturNumber);
        page.locator("#fakturDate").fill(YEAR + "-02-06");
        page.locator("#transactionCode").selectOption("01");
        page.locator("#btn-save-faktur").click();
        waitForPageLoad();
        assertThat(page.locator("[data-testid='chain-fakturs']")).containsText(fakturNumber);
        assertThat(page.locator("[data-testid='chain-fakturs']")).containsText("06/02/" + YEAR);
        assertThat(page.locator("[data-testid='chain-exceptions'] [data-type='PAID_NO_FAKTUR']")).hasCount(0);

        String bupotNumber = "B" + suffix();
        page.locator("#toggle-bupot-form").click();
        page.locator("#paymentId").selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(1));
        page.locator("#bupotNumber").fill(bupotNumber);
        page.locator("#bupotDate").fill(YEAR + "-02-25");
        page.locator("#taxObjectCode").selectOption("24-104-01");
        page.locator("#grossAmount").fill("10000000");
        page.locator("#taxRate").fill("2");
        page.locator("#taxAmount").fill("200000");
        page.locator("#btn-save-bupot").click();
        waitForPageLoad();
        assertThat(page.locator("[data-testid='chain-bupots']")).containsText(bupotNumber);
        assertThat(page.locator("[data-testid='chain-bupots']")).containsText("DRAFT");
        assertThat(page.locator("[data-testid='chain-exceptions']")).hasCount(0);

        navigateTo("/sales-tax-chain?masa=" + YEAR + "-02");
        waitForPageLoad();
        assertThat(page.locator("#recon-line-REVENUE_NO_FAKTUR [data-col='amount']")).hasText("0");
        assertThat(page.locator("#recon-dpp")).hasText("10.000.000");
        assertThat(page.locator("#effect-ppn")).hasText("1.100.000");
        assertThat(page.locator("#effect-invoice-value")).hasText("11.100.000");
        assertThat(page.locator("#effect-pph23")).hasText("0");
        assertThat(page.locator("#effect-pph23-receipts")).hasText("200.000");
        assertThat(exceptionRows(invoice)).hasCount(0);

        postRevenue(invoice);

        navigateTo("/sales-tax-chain?masa=" + YEAR + "-02");
        waitForPageLoad();
        assertThat(page.locator("#effect-pph23")).hasText("200.000");
        assertThat(page.locator("#effect-pph23-ledger")).hasText("200.000");
        assertThat(page.locator("#effect-pph23-diff")).hasText("0");
        assertThat(page.locator("#effect-net")).hasText("10.900.000");
    }

    @Test
    @DisplayName("An exception of an earlier masa stays listed in later masa and is absent from masa before it; the sidebar opens the report")
    void exceptionsCarryForward() {
        Client client = fixture.client("Pelanggan Lama");
        Invoice invoice = fixture.sentInvoice(client, LocalDate.of(YEAR, 5, 5), LocalDate.of(YEAR, 6, 5), "1000000", "11");
        fixture.payInvoice(invoice, LocalDate.of(YEAR, 5, 20), "1110000", "TRF");

        navigateTo("/sales-tax-chain?masa=" + YEAR + "-04");
        waitForPageLoad();
        assertThat(exceptionRows(invoice)).hasCount(0);

        navigateTo("/sales-tax-chain?masa=" + YEAR + "-05");
        waitForPageLoad();
        assertThat(exceptionRows(invoice)).hasCount(1);

        navigateTo("/sales-tax-chain?masa=" + YEAR + "-08");
        waitForPageLoad();
        assertThat(exceptionRows(invoice)).hasCount(1);
        assertThat(exceptionRows(invoice).first()).hasAttribute("data-type", "PAID_NO_FAKTUR");

        navigateTo("/dashboard");
        waitForPageLoad();
        page.evaluate("document.querySelectorAll('details').forEach(d => d.open = true)");
        page.locator("#nav-sales-tax-chain").click();
        waitForPageLoad();
        assertThat(page.locator("[data-testid='sales-tax-chain-report']")).isVisible();
    }

    @Test
    @DisplayName("A faktur number already used elsewhere is refused with a message and nothing is saved")
    void duplicateNumberRefused() {
        Client client = fixture.client("Pelanggan Nomor");
        Invoice first = fixture.sentInvoice(client, LocalDate.of(YEAR, 9, 1), LocalDate.of(YEAR, 10, 1), "1000000", "11");
        Invoice second = fixture.sentInvoice(client, LocalDate.of(YEAR, 9, 2), LocalDate.of(YEAR, 10, 2), "1000000", "11");
        String number = "F" + suffix();

        recordFaktur(first, number);
        assertThat(page.locator("[data-testid='chain-fakturs']")).containsText(number);

        recordFaktur(second, number);
        assertThat(page.locator("body")).containsText("Nomor faktur sudah digunakan: " + number);
        assertThat(page.locator("[data-testid='chain-no-faktur']")).isVisible();
    }

    private void recordFaktur(Invoice invoice, String number) {
        navigateTo("/invoices/" + invoice.getInvoiceNumber());
        waitForPageLoad();
        page.locator("#toggle-faktur-form").click();
        page.locator("#fakturNumber").fill(number);
        page.locator("#fakturDate").fill(YEAR + "-09-03");
        page.locator("#transactionCode").selectOption("01");
        page.locator("#btn-save-faktur").click();
        waitForPageLoad();
    }

    private Locator exceptionRows(Invoice invoice) {
        return page.locator("#table-exceptions tbody tr").filter(new Locator.FilterOptions().setHasText(invoice.getInvoiceNumber()));
    }

    /** Posts every draft of the invoice: revenue, and the bupot once recorded. */
    private void postRevenue(Invoice invoice) {
        transactionRepository.findBySourceDocumentTypeAndSourceDocumentIdOrderByTransactionDateAsc("INVOICE", invoice.getId())
                .stream().filter(t -> t.getStatus() == TransactionStatus.DRAFT)
                .forEach(t -> transactionService.post(t.getId(), "admin"));
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
