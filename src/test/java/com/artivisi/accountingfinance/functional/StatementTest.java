package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.Bill;
import com.artivisi.accountingfinance.entity.Client;
import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.entity.Vendor;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.ProductRepository;
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

import java.time.LocalDate;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Customer and vendor statements: opening balance from documents and payments before the period,
 * chronological invoice/bill and payment entries with running balance, closing balance, the date
 * filter, the empty period, the default period and the print layout.
 *
 * Each test creates its own client/vendor with documents dated in 2089.
 */
@DisplayName("Customer/Vendor Statements - Functional Tests")
@Import(ServiceTestDataInitializer.class)
class StatementTest extends PlaywrightTestBase {

    private static final int YEAR = 2089;

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
    @DisplayName("Client statement: opening balance, invoices and payments in date order with running balance, closing balance")
    void shouldShowClientStatement() {
        ClientHistory h = clientHistory();

        navigateTo("/statements/client/" + h.client.getCode() + "?dateFrom=" + YEAR + "-02-01&dateTo=" + YEAR + "-02-28");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Laporan Klien");
        assertThat(page.locator("[data-testid='client-statement'] h2")).hasText(h.client.getName());
        assertThat(page.locator("[data-testid='client-statement']")).containsText("Periode: 01 Feb " + YEAR + " - 28 Feb " + YEAR);
        assertThat(page.locator("#dateFrom")).hasValue(YEAR + "-02-01");
        assertThat(page.locator("#dateTo")).hasValue(YEAR + "-02-28");
        assertThat(page.locator("[data-testid='opening-balance'] td")).hasText(new String[] {
                "01 Feb " + YEAR, "Saldo Awal", "-", "-", "Rp 3,000,000"});
        assertThat(entryRows()).hasCount(4);
        assertThat(entryRows().nth(0).locator("td")).hasText(new String[] {
                "05 Feb " + YEAR, "Invoice", h.february.getInvoiceNumber(), "Invoice " + h.february.getInvoiceNumber(),
                "Rp 8,880,000", "-", "Rp 11,880,000"});
        assertThat(entryRows().nth(1).locator("td")).hasText(new String[] {
                "10 Feb " + YEAR, "Invoice", h.sameDay.getInvoiceNumber(), "Invoice " + h.sameDay.getInvoiceNumber(),
                "Rp 2,000,000", "-", "Rp 13,880,000"});
        assertThat(entryRows().nth(2).locator("td")).hasText(new String[] {
                "10 Feb " + YEAR, "Pembayaran", "TRF-FEB-1", "Pembayaran " + h.february.getInvoiceNumber(),
                "-", "Rp 3,000,000", "Rp 10,880,000"});
        assertThat(entryRows().nth(3).locator("td")).hasText(new String[] {
                "20 Feb " + YEAR, "Pembayaran", "TRF-FEB-2", "Pembayaran " + h.january.getInvoiceNumber(),
                "-", "Rp 1,000,000", "Rp 9,880,000"});
        assertThat(page.locator("[data-testid='closing-balance'] td")).hasText(new String[] {
                "28 Feb " + YEAR, "Saldo Akhir", "-", "-", "Rp 9,880,000"});
        assertThat(page.locator("[data-testid='no-data']")).hasCount(0);
        assertThat(page.locator("a:text-is('Cetak')")).hasAttribute("href",
                "/statements/client/" + h.client.getCode() + "/print?dateFrom=" + YEAR + "-02-01&dateTo=" + YEAR + "-02-28");
        assertThat(page.locator("[data-testid='client-statement'] nav a")).hasAttribute("href", "/clients/" + h.client.getCode());
    }

    @Test
    @DisplayName("Client statement date filter: January shows zero opening, the January invoice and payment")
    void shouldFilterClientStatementByDateRange() {
        ClientHistory h = clientHistory();

        navigateTo("/statements/client/" + h.client.getCode() + "?dateFrom=" + YEAR + "-02-01&dateTo=" + YEAR + "-02-28");
        waitForPageLoad();
        page.locator("#dateFrom").fill(YEAR + "-01-01");
        page.locator("#dateTo").fill(YEAR + "-01-31");
        page.locator("button:text-is('Tampilkan')").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/statements/client/" + h.client.getCode()
                + "?dateFrom=" + YEAR + "-01-01&dateTo=" + YEAR + "-01-31");
        assertThat(page.locator("[data-testid='opening-balance'] td").last()).hasText("Rp 0");
        assertThat(entryRows()).hasCount(2);
        assertThat(entryRows().nth(0).locator("td")).hasText(new String[] {
                "10 Jan " + YEAR, "Invoice", h.january.getInvoiceNumber(), "Invoice " + h.january.getInvoiceNumber(),
                "Rp 5,000,000", "-", "Rp 5,000,000"});
        assertThat(entryRows().nth(1).locator("td")).hasText(new String[] {
                "20 Jan " + YEAR, "Pembayaran", "TRF-JAN", "Pembayaran " + h.january.getInvoiceNumber(),
                "-", "Rp 2,000,000", "Rp 3,000,000"});
        assertThat(page.locator("[data-testid='closing-balance'] td").last()).hasText("Rp 3,000,000");

        navigateTo("/statements/client/" + h.client.getCode() + "?dateFrom=" + YEAR + "-03-01&dateTo=" + YEAR + "-03-31");
        waitForPageLoad();
        assertThat(page.locator("[data-testid='opening-balance'] td").last()).hasText("Rp 9,880,000");
        assertThat(entryRows()).hasCount(1);
        assertThat(entryRows().first().locator("td").nth(2)).hasText(h.march.getInvoiceNumber());
        assertThat(page.locator("[data-testid='closing-balance'] td").last()).hasText("Rp 10,380,000");
    }

    @Test
    @DisplayName("Client statement for a period without activity shows the no-data message with equal opening and closing")
    void shouldShowEmptyClientStatement() {
        ClientHistory h = clientHistory();

        navigateTo("/statements/client/" + h.client.getCode() + "?dateFrom=" + (YEAR + 1) + "-01-01&dateTo=" + (YEAR + 1) + "-01-31");
        waitForPageLoad();

        assertThat(entryRows()).hasCount(0);
        assertThat(page.locator("[data-testid='no-data']")).hasText("Tidak ada transaksi pada periode ini");
        assertThat(page.locator("[data-testid='opening-balance'] td").last()).hasText("Rp 10,380,000");
        assertThat(page.locator("[data-testid='closing-balance'] td").last()).hasText("Rp 10,380,000");
    }

    @Test
    @DisplayName("Statement without dates covers the first of the current month to today")
    void shouldDefaultToCurrentMonth() {
        Client client = fixture.client("Klien Default");

        navigateTo("/statements/client/" + client.getCode());
        waitForPageLoad();

        LocalDate today = LocalDate.now();
        assertThat(page.locator("#dateFrom")).hasValue(today.withDayOfMonth(1).toString());
        assertThat(page.locator("#dateTo")).hasValue(today.toString());
        assertThat(page.locator("[data-testid='no-data']")).hasCount(1);
    }

    @Test
    @DisplayName("Client statement print page lists the same balances and entries")
    void shouldPrintClientStatement() {
        ClientHistory h = clientHistory();

        navigateTo("/statements/client/" + h.client.getCode() + "/print?dateFrom=" + YEAR + "-02-01&dateTo=" + YEAR + "-02-28");
        waitForPageLoad();

        assertThat(page.locator("h1")).hasText("Laporan Piutang — " + h.client.getName());
        assertThat(page.locator(".header p.mono")).hasText(h.client.getCode());
        assertThat(page.locator(".period")).hasText("Periode: 01 Feb " + YEAR + " - 28 Feb " + YEAR);
        assertThat(page.locator("thead th").nth(4)).hasText("Invoice");
        Locator rows = page.locator("tbody tr");
        assertThat(rows).hasCount(6);
        assertThat(rows.nth(0).locator("td")).hasText(new String[] {"01 Feb " + YEAR, "Saldo Awal", "-", "-", "Rp 3,000,000"});
        assertThat(rows.nth(1).locator("td")).hasText(new String[] {
                "05 Feb " + YEAR, "Invoice", h.february.getInvoiceNumber(), "Invoice " + h.february.getInvoiceNumber(),
                "Rp 8,880,000", "-", "Rp 11,880,000"});
        assertThat(rows.nth(3).locator("td")).hasText(new String[] {
                "10 Feb " + YEAR, "Pembayaran", "TRF-FEB-1", "Pembayaran " + h.february.getInvoiceNumber(),
                "-", "Rp 3,000,000", "Rp 10,880,000"});
        assertThat(rows.nth(5).locator("td")).hasText(new String[] {"28 Feb " + YEAR, "Saldo Akhir", "-", "-", "Rp 9,880,000"});
    }

    @Test
    @DisplayName("Vendor statement: opening balance, bills and payments with running balance, closing balance")
    void shouldShowVendorStatement() {
        Vendor vendor = fixture.vendor("Vendor Laporan");
        Bill january = approved(vendor, LocalDate.of(YEAR, 1, 15), "4000000", null);
        fixture.payBill(january, LocalDate.of(YEAR, 1, 25), "1500000", "TRF-V-JAN");
        Bill february = approved(vendor, LocalDate.of(YEAR, 2, 12), "2000000", "11");
        fixture.payBill(february, LocalDate.of(YEAR, 2, 20), "2220000", "TRF-V-FEB");
        fixture.draftBill(vendor, LocalDate.of(YEAR, 2, 14), LocalDate.of(YEAR, 3, 14), "999000", null);

        navigateTo("/statements/vendor/" + vendor.getCode() + "?dateFrom=" + YEAR + "-02-01&dateTo=" + YEAR + "-02-28");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Laporan Vendor");
        assertThat(page.locator("[data-testid='vendor-statement'] h2")).hasText(vendor.getName());
        assertThat(page.locator("thead th").nth(4)).hasText("Tagihan");
        assertThat(page.locator("[data-testid='opening-balance'] td").last()).hasText("Rp 2,500,000");
        assertThat(entryRows()).hasCount(2);
        assertThat(entryRows().nth(0).locator("td")).hasText(new String[] {
                "12 Feb " + YEAR, "Tagihan", february.getBillNumber(), "Tagihan " + february.getBillNumber(),
                "Rp 2,220,000", "-", "Rp 4,720,000"});
        assertThat(entryRows().nth(1).locator("td")).hasText(new String[] {
                "20 Feb " + YEAR, "Pembayaran", "TRF-V-FEB", "Pembayaran " + february.getBillNumber(),
                "-", "Rp 2,220,000", "Rp 2,500,000"});
        assertThat(page.locator("[data-testid='closing-balance'] td").last()).hasText("Rp 2,500,000");

        navigateTo("/statements/vendor/" + vendor.getCode() + "/print?dateFrom=" + YEAR + "-02-01&dateTo=" + YEAR + "-02-28");
        waitForPageLoad();
        assertThat(page.locator("h1")).hasText("Laporan Hutang — " + vendor.getName());
        Locator rows = page.locator("tbody tr");
        assertThat(rows).hasCount(4);
        assertThat(rows.nth(1).locator("td")).hasText(new String[] {
                "12 Feb " + YEAR, "Tagihan", february.getBillNumber(), "Tagihan " + february.getBillNumber(),
                "Rp 2,220,000", "-", "Rp 4,720,000"});
        assertThat(rows.nth(3).locator("td").last()).hasText("Rp 2,500,000");
    }

    /**
     * January: invoice 5,000,000 paid 2,000,000 (opening for February 3,000,000). February: invoice
     * 8,000,000 + 11% PPN, a same-day invoice 2,000,000 listed before a 3,000,000 payment, and a
     * 1,000,000 payment on the January invoice. March: invoice 500,000. A draft and a cancelled
     * invoice in February are excluded.
     */
    private ClientHistory clientHistory() {
        Client client = fixture.client("Klien Laporan");
        Invoice january = sent(client, LocalDate.of(YEAR, 1, 10), "5000000", null);
        fixture.payInvoice(january, LocalDate.of(YEAR, 1, 20), "2000000", "TRF-JAN");
        Invoice february = sent(client, LocalDate.of(YEAR, 2, 5), "8000000", "11");
        Invoice sameDay = sent(client, LocalDate.of(YEAR, 2, 10), "2000000", null);
        fixture.payInvoice(february, LocalDate.of(YEAR, 2, 10), "3000000", "TRF-FEB-1");
        fixture.payInvoice(january, LocalDate.of(YEAR, 2, 20), "1000000", "TRF-FEB-2");
        fixture.draftInvoice(client, LocalDate.of(YEAR, 2, 15), LocalDate.of(YEAR, 3, 15), "777000", null);
        Invoice cancelled = sent(client, LocalDate.of(YEAR, 2, 16), "666000", null);
        invoiceService.cancel(cancelled.getId());
        Invoice march = sent(client, LocalDate.of(YEAR, 3, 5), "500000", null);
        return new ClientHistory(client, january, february, sameDay, march);
    }

    private Invoice sent(Client client, LocalDate invoiceDate, String amount, String taxRate) {
        return fixture.sentInvoice(client, invoiceDate, invoiceDate.plusDays(30), amount, taxRate);
    }

    private Bill approved(Vendor vendor, LocalDate billDate, String amount, String taxRate) {
        return fixture.approvedBill(vendor, billDate, billDate.plusDays(30), amount, taxRate);
    }

    private Locator entryRows() {
        return page.locator("[data-testid='statement-table'] tbody tr:not([data-testid])");
    }

    private record ClientHistory(Client client, Invoice january, Invoice february, Invoice sameDay, Invoice march) {
    }
}
