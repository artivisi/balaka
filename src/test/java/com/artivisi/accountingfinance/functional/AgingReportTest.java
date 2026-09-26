package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.dto.AgingBucket;
import com.artivisi.accountingfinance.dto.AgingReport;
import com.artivisi.accountingfinance.entity.Bill;
import com.artivisi.accountingfinance.entity.Client;
import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.entity.Vendor;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.ProductRepository;
import com.artivisi.accountingfinance.service.AgingReportService;
import com.artivisi.accountingfinance.service.BillService;
import com.artivisi.accountingfinance.service.ClientService;
import com.artivisi.accountingfinance.service.InvoiceService;
import com.artivisi.accountingfinance.service.VendorService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Receivables and payables aging: outstanding balances per client/vendor placed in the current,
 * 1-30, 31-60, 61-90 and over-90-days buckets by due date relative to the as-of date, excluding
 * draft, paid and cancelled documents and net of partial payments; bucket boundaries; the as-of
 * filter; summary cards and table totals; sidebar navigation.
 *
 * Each test creates its own client/vendor and documents with due dates in 2088 and asserts that
 * client's/vendor's row exactly. Totals cover every outstanding document in the shared database,
 * so they are checked against the service for the same as-of date.
 */
@DisplayName("Aging Reports - Functional Tests")
@Import(ServiceTestDataInitializer.class)
class AgingReportTest extends PlaywrightTestBase {

    private static final LocalDate AS_OF = LocalDate.of(2088, 6, 30);

    @Autowired private InvoiceService invoiceService;
    @Autowired private BillService billService;
    @Autowired private ClientService clientService;
    @Autowired private VendorService vendorService;
    @Autowired private ProductRepository productRepository;
    @Autowired private ChartOfAccountRepository chartOfAccountRepository;
    @Autowired private AgingReportService agingReportService;

    private ArApFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new ArApFixture(clientService, vendorService, invoiceService, billService,
                productRepository, chartOfAccountRepository);
        loginAsAdmin();
    }

    @Test
    @DisplayName("Receivables: each bucket holds the client's outstanding balance; draft, paid and cancelled excluded")
    void shouldBucketReceivablesPerClient() {
        Client client = fixture.client("Klien Umur");
        sent(client, AS_OF.plusDays(15), "1000000", "11");
        Invoice partlyPaid = sent(client, AS_OF.minusDays(20), "2000000", null);
        fixture.payInvoice(partlyPaid, AS_OF.minusDays(5), "500000", "TRF-P");
        sent(client, AS_OF.minusDays(46), "3000000", null);
        sent(client, AS_OF.minusDays(76), "4000000", null);
        sent(client, AS_OF.minusDays(150), "5000000", null);
        Invoice paid = sent(client, AS_OF.minusDays(29), "700000", null);
        fixture.payInvoice(paid, AS_OF.minusDays(10), "700000", "TRF-L");
        fixture.draftInvoice(client, AS_OF.minusDays(59), AS_OF.minusDays(29), "900000", null);
        Invoice cancelled = sent(client, AS_OF.minusDays(40), "800000", null);
        invoiceService.cancel(cancelled.getId());

        Client settled = fixture.client("Klien Lunas");
        fixture.payInvoice(sent(settled, AS_OF.minusDays(10), "600000", null), AS_OF.minusDays(1), "600000", "TRF-S");

        navigateTo("/reports/aging/receivables?asOfDate=" + AS_OF);
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Umur Piutang");
        assertThat(page.locator("#asOfDate")).hasValue(AS_OF.toString());
        assertThat(page.locator("[data-testid='aging-receivables'] h2 + p")).hasText("Per tanggal 30 Juni 2088");
        assertRow(client.getCode(), client.getName(), "1,110,000", "1,500,000", "3,000,000", "4,000,000", "5,000,000", "14,610,000");
        assertThat(row(settled.getCode())).hasCount(0);
        assertTotals(agingReportService.generateReceivablesAging(AS_OF));
    }

    @Test
    @DisplayName("Receivables: bucket boundaries at 0, 30/31, 60/61 and 90/91 days past due")
    void shouldApplyBucketBoundaries() {
        Client client = fixture.client("Klien Batas");
        sent(client, AS_OF, "100000", null);
        sent(client, AS_OF.minusDays(30), "200000", null);
        sent(client, AS_OF.minusDays(31), "300000", null);
        sent(client, AS_OF.minusDays(60), "400000", null);
        sent(client, AS_OF.minusDays(61), "500000", null);
        sent(client, AS_OF.minusDays(90), "600000", null);
        sent(client, AS_OF.minusDays(91), "700000", null);

        navigateTo("/reports/aging/receivables?asOfDate=" + AS_OF);
        waitForPageLoad();

        assertRow(client.getCode(), client.getName(), "100,000", "200,000", "700,000", "1,100,000", "700,000", "2,800,000");
    }

    @Test
    @DisplayName("Receivables: changing the as-of date through the filter re-ages the same invoices")
    void shouldReAgeReceivablesForAnotherAsOfDate() {
        Client client = fixture.client("Klien Geser");
        sent(client, AS_OF.plusDays(15), "1000000", null);
        sent(client, AS_OF.minusDays(20), "2000000", null);
        sent(client, AS_OF.minusDays(76), "4000000", null);

        navigateTo("/reports/aging/receivables?asOfDate=" + AS_OF);
        waitForPageLoad();
        assertRow(client.getCode(), client.getName(), "1,000,000", "2,000,000", "0", "4,000,000", "0", "7,000,000");

        LocalDate later = AS_OF.plusMonths(1);
        page.locator("#asOfDate").fill(later.toString());
        page.locator("#btn-generate").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/reports/aging/receivables?asOfDate=" + later);
        assertThat(page.locator("#asOfDate")).hasValue(later.toString());
        assertRow(client.getCode(), client.getName(), "0", "1,000,000", "2,000,000", "0", "4,000,000", "7,000,000");
        assertTotals(agingReportService.generateReceivablesAging(later));
    }

    @Test
    @DisplayName("Payables: each bucket holds the vendor's outstanding balance; draft, paid and cancelled bills excluded")
    void shouldBucketPayablesPerVendor() {
        Vendor vendor = fixture.vendor("Vendor Umur");
        approved(vendor, AS_OF.plusDays(15), "1000000", "11");
        Bill partlyPaid = approved(vendor, AS_OF.minusDays(20), "2000000", null);
        fixture.payBill(partlyPaid, AS_OF.minusDays(5), "500000", "TRF-P");
        approved(vendor, AS_OF.minusDays(45), "2500000", null);
        approved(vendor, AS_OF.minusDays(150), "3000000", null);
        Bill paid = approved(vendor, AS_OF.minusDays(29), "700000", null);
        fixture.payBill(paid, AS_OF.minusDays(10), "700000", "TRF-L");
        fixture.draftBill(vendor, AS_OF.minusDays(59), AS_OF.minusDays(29), "900000", null);
        Bill cancelled = approved(vendor, AS_OF.minusDays(40), "800000", null);
        billService.cancel(cancelled.getId());

        navigateTo("/reports/aging/payables?asOfDate=" + AS_OF);
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Umur Hutang");
        assertThat(page.locator("#asOfDate")).hasValue(AS_OF.toString());
        assertRow(vendor.getCode(), vendor.getName(), "1,110,000", "1,500,000", "2,500,000", "0", "3,000,000", "8,110,000");
        assertTotals(agingReportService.generatePayablesAging(AS_OF));
    }

    @Test
    @DisplayName("Sidebar links open both aging reports as of today")
    void shouldOpenAgingReportsFromSidebar() {
        String today = LocalDate.now().toString();
        String heading = "Per tanggal " + LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.of("id", "ID")));

        navigateTo("/dashboard");
        waitForPageLoad();
        page.locator("#nav-group-laporan").click();
        page.locator("#nav-aging-receivables").click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/reports/aging/receivables");
        assertThat(page.locator("#page-title")).hasText("Umur Piutang");
        assertThat(page.locator("#asOfDate")).hasValue(today);
        assertThat(page.locator("[data-testid='aging-receivables'] h2 + p")).hasText(heading);

        navigateTo("/dashboard");
        waitForPageLoad();
        page.locator("#nav-group-laporan").click();
        page.locator("#nav-aging-payables").click();
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/reports/aging/payables");
        assertThat(page.locator("#page-title")).hasText("Umur Hutang");
        assertThat(page.locator("#asOfDate")).hasValue(today);
    }

    private Invoice sent(Client client, LocalDate dueDate, String amount, String taxRate) {
        return fixture.sentInvoice(client, dueDate.minusDays(30), dueDate, amount, taxRate);
    }

    private Bill approved(Vendor vendor, LocalDate dueDate, String amount, String taxRate) {
        return fixture.approvedBill(vendor, dueDate.minusDays(30), dueDate, amount, taxRate);
    }

    private Locator row(String code) {
        return page.locator("[data-testid='aging-row-" + code + "']");
    }

    private void assertRow(String code, String name, String... amounts) {
        Locator row = row(code);
        assertThat(row).hasCount(1);
        Locator cells = row.locator("td");
        assertThat(cells).hasCount(7);
        assertThat(cells.first().locator("div")).hasText(new String[] {name, code});
        for (int i = 0; i < amounts.length; i++) {
            assertThat(cells.nth(i + 1)).hasText("Rp " + amounts[i]);
        }
    }

    private void assertTotals(AgingReport report) {
        AgingBucket totals = report.totals();
        String[] expected = {rp(totals.current()), rp(totals.days1to30()), rp(totals.days31to60()),
                rp(totals.days61to90()), rp(totals.over90()), rp(totals.total())};
        assertThat(page.locator("[data-testid='aging-summary'] p.font-mono")).hasText(expected);
        assertThat(page.locator("[data-testid='aging-table'] tfoot td:not(:first-child)")).hasText(expected);
    }

    private static String rp(BigDecimal amount) {
        return "Rp " + new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US)).format(amount);
    }
}
