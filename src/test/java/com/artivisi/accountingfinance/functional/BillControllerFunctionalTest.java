package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.Bill;
import com.artivisi.accountingfinance.entity.BillLine;
import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.Vendor;
import com.artivisi.accountingfinance.enums.BillStatus;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.BillRepository;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.service.BillService;
import com.artivisi.accountingfinance.service.TransactionService;
import com.artivisi.accountingfinance.service.VendorService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BillController: list filters, new/edit form (vendor and expense account pickers, line items),
 * validation, detail page, approve (expense recognition drafts and their journals), mark paid,
 * cancel and delete.
 *
 * Every test creates its own vendor and bills; bill dates are in 2085 so the approval journals
 * posted here stay out of other classes' reporting periods.
 */
@DisplayName("Bill Controller Tests")
@Import(ServiceTestDataInitializer.class)
class BillControllerFunctionalTest extends PlaywrightTestBase {

    private static final int YEAR = 2085;
    private static final String CLOUD = "5.1.20";
    private static final String SOFTWARE = "5.1.21";
    private static final String INPUT_VAT = "1.1.25";
    private static final String PAYABLE = "2.1.01";

    @Autowired private BillService billService;
    @Autowired private BillRepository billRepository;
    @Autowired private VendorService vendorService;
    @Autowired private ChartOfAccountRepository chartOfAccountRepository;
    @Autowired private TransactionService transactionService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Should list a vendor's bills and narrow them by status through the filter form")
    void shouldListBillsByVendorAndStatus() {
        Vendor vendor = createVendor();
        Bill draft = createBill(vendor, "FV-" + shortId(), LocalDate.of(YEAR, 1, 10), line("Sewa server", CLOUD, "1", "1000000", "11"));
        Bill approved = createBill(vendor, null, LocalDate.of(YEAR, 1, 20), line("Lisensi", SOFTWARE, "2", "250000", null));
        asAdmin(() -> billService.approve(approved.getId()));

        navigateTo("/bills?vendorId=" + vendor.getId());
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Daftar Tagihan");
        assertThat(page.locator("#vendor-picker-input")).hasValue(vendor.getCode() + " - " + vendor.getName());
        assertThat(billRows()).hasCount(2);
        assertThat(billRows().nth(0).locator("td")).hasText(new String[] {
                approved.getBillNumber(), vendor.getName(), "-", "20 Jan " + YEAR, "19 Feb " + YEAR,
                "Rp 500,000", "Disetujui", "Detail"});
        assertThat(billRows().nth(1).locator("td")).hasText(new String[] {
                draft.getBillNumber(), vendor.getName(), draft.getVendorInvoiceNumber(), "10 Jan " + YEAR,
                "09 Feb " + YEAR, "Rp 1,000,000", "Draf", "Detail"});
        assertThat(billRows().nth(1).locator("td").first().locator("a"))
                .hasAttribute("href", "/bills/" + draft.getBillNumber());

        page.locator("#filter-status").selectOption("APPROVED");
        page.locator("[data-testid='bill-list'] form button:text-is('Filter')").click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/bills?status=APPROVED&vendorId=" + vendor.getId());
        assertThat(page.locator("#filter-status")).hasValue("APPROVED");
        assertThat(billRows()).hasCount(1);
        assertThat(billRows().first().locator("td").first()).hasText(approved.getBillNumber());

        navigateTo("/bills?status=PAID&vendorId=" + vendor.getId());
        waitForPageLoad();
        assertThat(billRows()).hasCount(0);
        assertThat(page.locator("[data-testid='bill-table'] tbody")).hasText("Belum ada tagihan");
    }

    @Test
    @DisplayName("Should open the new bill form with one blank line")
    void shouldDisplayNewBillForm() {
        navigateTo("/bills");
        waitForPageLoad();
        page.locator("#btn-new-bill").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/bills/new", page.url());
        assertThat(page.locator("#page-title")).hasText("Tagihan Baru");
        assertThat(form()).hasAttribute("action", "/bills/new");
        assertThat(page.locator("#billNumber")).hasValue("");
        assertThat(page.locator("#billNumber")).hasAttribute("placeholder", "Auto-generated jika kosong");
        assertThat(page.locator("#vendorLabel")).hasValue("");
        assertThat(page.locator("#billDate")).hasValue("");
        assertThat(page.locator("#dueDate")).hasValue("");
        assertThat(page.locator("input[name='lineDescription']")).hasCount(1);
        assertThat(page.locator("#bill-lineQuantity-0")).hasValue("1");
        assertThat(page.locator("#bill-lineUnitPrice-0")).hasValue("0");
        assertThat(totalsValue("Total")).hasText("Rp 0");
        assertThat(form().locator("a:text-is('Batal')")).hasAttribute("href", "/bills");
    }

    @Test
    @DisplayName("Should create a two-line bill with PPN through the form; auto number, totals and lines persisted")
    void shouldCreateBillThroughForm() {
        Vendor vendor = createVendor();
        String vendorInvoice = "FV-" + shortId();

        navigateTo("/bills/new");
        waitForPageLoad();
        selectVendor(vendor);
        page.locator("#vendorInvoiceNumber").fill(vendorInvoice);
        page.locator("#billDate").fill(YEAR + "-03-01");
        page.locator("#dueDate").fill(YEAR + "-03-31");
        fillLine(0, "Sewa server, Maret", CLOUD, "2", "1500000", "11");
        page.locator("button:text-is('+ Tambah Item')").click();
        fillLine(1, "Lisensi IDE", SOFTWARE, "1", "2000000", "");
        page.locator("#notes").fill("Tagihan bulanan");

        assertThat(totalsValue("Subtotal")).hasText("Rp 5.000.000");
        assertThat(totalsValue("PPN")).hasText("Rp 330.000");
        assertThat(totalsValue("Total")).hasText("Rp 5.330.000");

        submit();

        String prefix = "BILL-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-";
        assertThat(page).hasURL(Pattern.compile(".*/bills/" + prefix + "\\d{4}$"));
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Tagihan berhasil dibuat");
        String billNumber = page.url().substring(page.url().lastIndexOf('/') + 1);

        Bill bill = billService.findByBillNumber(billNumber);
        assertEquals(vendor.getId(), bill.getVendor().getId());
        assertEquals(vendorInvoice, bill.getVendorInvoiceNumber());
        assertEquals(LocalDate.of(YEAR, 3, 1), bill.getBillDate());
        assertEquals(LocalDate.of(YEAR, 3, 31), bill.getDueDate());
        assertEquals("Tagihan bulanan", bill.getNotes());
        assertEquals(BillStatus.DRAFT, bill.getStatus());
        assertEquals(0, bill.getAmount().compareTo(new BigDecimal("5000000")));
        assertEquals(0, bill.getTaxAmount().compareTo(new BigDecimal("330000")));
        assertEquals(List.of(
                "0|Sewa server, Maret|" + CLOUD + "|2.00|1500000.00|11.00|3000000.00|330000.00",
                "1|Lisensi IDE|" + SOFTWARE + "|1.00|2000000.00|null|2000000.00|0.00"), billLines(bill));

        assertThat(page.locator("[data-testid='bill-detail'] h2")).hasText(billNumber);
        assertThat(page.locator("[data-testid='bill-status']")).hasText("Draf");
        assertThat(page.locator("[data-testid='bill-detail'] a:text-is('" + vendor.getName() + "')"))
                .hasAttribute("href", "/vendors/" + vendor.getCode());
        assertThat(page.locator("[data-testid='bill-detail']")).containsText("No. Faktur Vendor: " + vendorInvoice);
        assertThat(infoValue("Tanggal Tagihan")).hasText("01 Maret " + YEAR);
        assertThat(infoValue("Jatuh Tempo")).hasText("31 Maret " + YEAR);
        assertThat(infoValue("Total")).hasText("Rp 5,330,000");
        assertThat(infoValue("PPN")).hasText("Rp 330,000");
        assertThat(infoValue("Catatan")).hasText("Tagihan bulanan");
        Locator lineRows = page.locator("table:has(th:text-is('Akun Beban')) tbody tr");
        assertThat(lineRows).hasCount(2);
        assertThat(lineRows.nth(0).locator("td")).hasText(new String[] {
                "1", "Sewa server, Maret", CLOUD + " - Beban Cloud & Server", "2.00", "Rp 1,500,000", "11.00%", "Rp 3,000,000"});
        assertThat(lineRows.nth(1).locator("td")).hasText(new String[] {
                "2", "Lisensi IDE", SOFTWARE + " - Beban Software & Lisensi", "1.00", "Rp 2,000,000", "-", "Rp 2,000,000"});
        Locator footer = page.locator("table:has(th:text-is('Akun Beban')) tfoot tr");
        assertThat(footer).hasText(new String[] {"Subtotal Rp 5,000,000", "PPN Rp 330,000", "Total Rp 5,330,000"});
        assertThat(page.locator("#form-approve")).hasCount(1);
        assertThat(page.locator("#form-paid")).hasCount(0);
        assertThat(page.locator("[data-testid='payment-form-section']")).hasCount(0);
    }

    @Test
    @DisplayName("Should reject a bill whose vendor was typed but not selected, keeping the entered lines")
    void shouldRejectBillWithoutSelectedVendor() {
        long before = billRepository.count();

        navigateTo("/bills/new");
        waitForPageLoad();
        page.locator("#vendorLabel").fill("Vendor Ketik Saja");
        page.locator("#billDate").fill(YEAR + "-04-01");
        page.locator("#dueDate").fill(YEAR + "-04-30");
        fillLine(0, "Jasa instalasi", CLOUD, "1", "750000", "");
        submit();

        assertEquals(baseUrl() + "/bills/new", page.url());
        assertThat(errors()).hasText(new String[] {"Vendor wajib diisi"});
        assertThat(page.locator("#vendorLabel")).hasValue("Vendor Ketik Saja");
        assertThat(page.locator("#billDate")).hasValue(YEAR + "-04-01");
        assertThat(page.locator("#bill-lineDescription-0")).hasValue("Jasa instalasi");
        assertThat(page.locator("#bill-lineExpenseAccountLabel-0")).hasValue(CLOUD + " - Beban Cloud & Server");
        assertThat(page.locator("#bill-lineUnitPrice-0")).hasValue("750000");
        assertEquals(before, billRepository.count());
    }

    @Test
    @DisplayName("Should reject a bill without line items and without dates; nothing persisted")
    void shouldRejectBillWithoutLinesOrDates() {
        Vendor vendor = createVendor();
        long before = billRepository.count();

        navigateTo("/bills/new");
        waitForPageLoad();
        selectVendor(vendor);
        page.locator("#billDate").evaluate("el => el.removeAttribute('required')");
        page.locator("#dueDate").evaluate("el => el.removeAttribute('required')");
        page.locator("button[title='Hapus']").click();
        assertThat(page.locator("input[name='lineDescription']")).hasCount(0);
        submit();

        assertEquals(baseUrl() + "/bills/new", page.url());
        assertThat(errors()).containsText(new String[] {"Minimal satu item tagihan wajib diisi"});
        assertThat(page.locator("form ul li:text-is('Tanggal tagihan wajib diisi')")).hasCount(1);
        assertThat(page.locator("form ul li:text-is('Tanggal jatuh tempo wajib diisi')")).hasCount(1);
        assertThat(page.locator("#vendorLabel")).hasValue(vendor.getCode() + " - " + vendor.getName());
        assertEquals(before, billRepository.count());
    }

    @Test
    @DisplayName("Should reject a duplicate bill number and keep the existing bill unchanged")
    void shouldRejectDuplicateBillNumber() {
        Vendor vendor = createVendor();
        Bill existing = createBill(vendor, null, LocalDate.of(YEAR, 5, 1), line("Asli", CLOUD, "1", "100000", null));
        long before = billRepository.count();

        navigateTo("/bills/new");
        waitForPageLoad();
        page.locator("#billNumber").fill(existing.getBillNumber());
        selectVendor(vendor);
        page.locator("#billDate").fill(YEAR + "-05-02");
        page.locator("#dueDate").fill(YEAR + "-06-01");
        fillLine(0, "Duplikat", CLOUD, "1", "999000", "");
        submit();

        assertThat(errors()).hasText(new String[] {"Nomor tagihan sudah digunakan: " + existing.getBillNumber()});
        assertThat(page.locator("#bill-lineDescription-0")).hasValue("Duplikat");
        assertEquals(before, billRepository.count());
        Bill unchanged = billService.findByBillNumber(existing.getBillNumber());
        assertEquals(existing.getId(), unchanged.getId());
        assertEquals(0, unchanged.getAmount().compareTo(new BigDecimal("100000")));
    }

    @Test
    @DisplayName("Should edit a draft bill from the detail page: prefilled form, new number, lines and PPN")
    void shouldUpdateDraftBill() {
        Vendor vendor = createVendor();
        Bill bill = createBill(vendor, "FV-" + shortId(), LocalDate.of(YEAR, 6, 1), line("Hosting", CLOUD, "1", "400000", null));
        String newNumber = "BILL-UPD-" + shortId();

        navigateTo("/bills/" + bill.getBillNumber());
        waitForPageLoad();
        page.locator("[data-testid='bill-detail'] a:text-is('Edit')").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/bills/" + bill.getBillNumber() + "/edit", page.url());
        assertThat(page.locator("#page-title")).hasText("Edit Tagihan");
        assertThat(form()).hasAttribute("action", "/bills/" + bill.getBillNumber());
        assertThat(page.locator("#billNumber")).hasValue(bill.getBillNumber());
        assertThat(page.locator("#vendorLabel")).hasValue(vendor.getCode() + " - " + vendor.getName());
        assertThat(page.locator("#vendorInvoiceNumber")).hasValue(bill.getVendorInvoiceNumber());
        assertThat(page.locator("#billDate")).hasValue(YEAR + "-06-01");
        assertThat(page.locator("#dueDate")).hasValue(YEAR + "-07-01");
        assertThat(page.locator("input[name='lineDescription']")).hasCount(1);
        assertThat(page.locator("#bill-lineDescription-0")).hasValue("Hosting");
        assertThat(page.locator("#bill-lineExpenseAccountLabel-0")).hasValue(CLOUD + " - Beban Cloud & Server");
        assertThat(page.locator("#bill-lineUnitPrice-0")).hasValue("400000");
        assertThat(form().locator("a:text-is('Batal')")).hasAttribute("href", "/bills/" + bill.getBillNumber());

        page.locator("#billNumber").fill(newNumber);
        page.locator("#dueDate").fill(YEAR + "-06-15");
        fillLine(0, "Hosting Juni", SOFTWARE, "3", "400000", "11");
        submit();

        assertEquals(baseUrl() + "/bills/" + newNumber, page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Tagihan berhasil diperbarui");
        assertThat(infoValue("Total")).hasText("Rp 1,332,000");

        Bill updated = billService.findByBillNumber(newNumber);
        assertEquals(bill.getId(), updated.getId());
        assertEquals(LocalDate.of(YEAR, 6, 15), updated.getDueDate());
        assertEquals(0, updated.getAmount().compareTo(new BigDecimal("1200000")));
        assertEquals(0, updated.getTaxAmount().compareTo(new BigDecimal("132000")));
        assertEquals(List.of("0|Hosting Juni|" + SOFTWARE + "|3.00|400000.00|11.00|1200000.00|132000.00"), billLines(updated));
        assertFalse(billRepository.existsByBillNumber(bill.getBillNumber()));
    }

    @Test
    @DisplayName("Should reject a blank or another bill's number on update; form stays bound to the edited bill")
    void shouldRejectUpdateToDuplicateBillNumber() {
        Vendor vendor = createVendor();
        Bill bill = createBill(vendor, null, LocalDate.of(YEAR, 7, 1), line("Edit", CLOUD, "1", "100000", null));
        Bill other = createBill(vendor, null, LocalDate.of(YEAR, 7, 2), line("Lain", CLOUD, "1", "200000", null));
        String freeNumber = "BILL-FIX-" + shortId();

        navigateTo("/bills/" + bill.getBillNumber() + "/edit");
        waitForPageLoad();
        page.locator("#billNumber").fill("");
        submit();

        assertThat(errors()).hasText(new String[] {"Nomor tagihan wajib diisi"});
        assertThat(form()).hasAttribute("action", "/bills/" + bill.getBillNumber());
        assertEquals(bill.getBillNumber(), billService.findById(bill.getId()).getBillNumber());

        page.locator("#billNumber").fill(other.getBillNumber());
        fillLine(0, "Edit baru", CLOUD, "1", "150000", "");
        submit();

        assertThat(page.locator("#page-title")).hasText("Edit Tagihan");
        assertThat(errors()).hasText(new String[] {"Nomor tagihan sudah digunakan: " + other.getBillNumber()});
        assertThat(form()).hasAttribute("action", "/bills/" + bill.getBillNumber());
        assertThat(page.locator("#bill-lineDescription-0")).hasValue("Edit baru");
        assertEquals(0, billService.findById(other.getId()).getAmount().compareTo(new BigDecimal("200000")));

        page.locator("#billNumber").fill(freeNumber);
        submit();

        assertEquals(baseUrl() + "/bills/" + freeNumber, page.url());
        Bill updated = billService.findByBillNumber(freeNumber);
        assertEquals(bill.getId(), updated.getId());
        assertEquals(0, updated.getAmount().compareTo(new BigDecimal("150000")));
        Bill untouched = billService.findById(other.getId());
        assertEquals(other.getBillNumber(), untouched.getBillNumber());
        assertEquals(List.of("0|Lain|" + CLOUD + "|1.00|200000.00|null|200000.00|0.00"), billLines(untouched));
    }

    @Test
    @DisplayName("Approving creates one expense draft per expense account; posted journals debit expense and PPN, credit payable")
    void shouldApproveBillAndRecognizeExpense() {
        Vendor vendor = createVendor();
        Bill bill = createBill(vendor, null, LocalDate.of(YEAR, 8, 5),
                line("Server A", CLOUD, "2", "1000000", "11"),
                line("Lisensi", SOFTWARE, "1", "500000", null),
                line("Server B", CLOUD, "1", "300000", "11"));

        navigateTo("/bills/" + bill.getBillNumber());
        waitForPageLoad();
        List<String> dialogs = new ArrayList<>();
        page.onceDialog(dialog -> {
            dialogs.add(dialog.message());
            dialog.accept();
        });
        page.locator("#form-approve button").click();
        waitForPageLoad();

        assertEquals(List.of("Setujui tagihan ini?"), dialogs);
        assertEquals(baseUrl() + "/bills/" + bill.getBillNumber(), page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Tagihan berhasil disetujui");
        assertThat(page.locator("[data-testid='bill-status']")).hasText("Disetujui");
        assertThat(infoValue("Disetujui")).containsText("oleh admin");
        assertThat(page.locator("[data-testid='balance-due']")).hasText("Rp 3,053,000");
        assertThat(page.locator("#form-approve")).hasCount(0);
        assertThat(page.locator("[data-testid='bill-detail'] a:text-is('Edit')")).hasCount(0);
        assertThat(page.locator("#form-paid")).hasCount(1);
        assertThat(page.locator("[data-testid='payment-form-section']")).hasCount(1);

        Bill approved = billService.findById(bill.getId());
        assertEquals(BillStatus.APPROVED, approved.getStatus());
        assertEquals("admin", approved.getApprovedBy());
        assertNotNull(approved.getApprovedAt());

        List<String> drafts = billTransactions(bill);
        assertEquals(List.of(
                "DRAFT|" + YEAR + "-08-05|2553000.00|Tagihan " + bill.getBillNumber() + " - Beban Cloud & Server|Tagihan Vendor",
                "DRAFT|" + YEAR + "-08-05|500000.00|Tagihan " + bill.getBillNumber() + " - Beban Software & Lisensi|Tagihan Vendor"),
                drafts);

        List<UUID> ids = billTransactionIds(bill);
        asAdmin(() -> ids.forEach(id -> transactionService.post(id, "admin")));
        assertEquals(List.of("DEBIT:" + INPUT_VAT + ":253000.00", "DEBIT:" + CLOUD + ":2300000.00",
                "CREDIT:" + PAYABLE + ":2553000.00"), journalLines(ids.get(0)));
        assertEquals(List.of("DEBIT:" + SOFTWARE + ":500000.00", "CREDIT:" + PAYABLE + ":500000.00"),
                journalLines(ids.get(1)));

        navigateTo("/bills/" + bill.getBillNumber() + "/edit");
        waitForPageLoad();
        assertEquals(baseUrl() + "/bills/" + bill.getBillNumber(), page.url());
    }

    @Test
    @DisplayName("Should refuse approval when a line has no expense account; bill stays draft without journals")
    void shouldRefuseApprovalWithoutExpenseAccount() {
        Vendor vendor = createVendor();
        Bill bill = createBill(vendor, null, LocalDate.of(YEAR, 9, 1),
                line("Ada akun", CLOUD, "1", "100000", null),
                line("Tanpa akun", null, "1", "50000", null));

        navigateTo("/bills/" + bill.getBillNumber());
        waitForPageLoad();
        page.onceDialog(dialog -> dialog.accept());
        page.locator("#form-approve button").click();
        waitForPageLoad();

        assertThat(page.locator("[data-testid='flash-error']")).hasText("Baris tagihan 'Tanpa akun' belum memiliki Akun Beban");
        assertThat(page.locator("[data-testid='flash-success']")).hasCount(0);
        assertThat(page.locator("[data-testid='bill-status']")).hasText("Draf");
        assertEquals(BillStatus.DRAFT, billService.findById(bill.getId()).getStatus());
        assertEquals(List.of(), billTransactions(bill));
    }

    @Test
    @DisplayName("Should mark an approved bill paid after confirmation")
    void shouldMarkApprovedBillPaid() {
        Vendor vendor = createVendor();
        Bill bill = createBill(vendor, null, LocalDate.of(YEAR, 10, 1), line("Hosting", CLOUD, "1", "800000", null));
        asAdmin(() -> billService.approve(bill.getId()));

        navigateTo("/bills/" + bill.getBillNumber());
        waitForPageLoad();
        List<String> dialogs = new ArrayList<>();
        page.onceDialog(dialog -> {
            dialogs.add(dialog.message());
            dialog.accept();
        });
        page.locator("#form-paid button").click();
        waitForPageLoad();

        assertEquals(List.of("Tandai tagihan ini sebagai lunas?"), dialogs);
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Tagihan ditandai sudah dibayar");
        assertThat(page.locator("[data-testid='bill-status']")).hasText("Lunas");
        assertThat(page.locator("[data-testid='balance-due']")).hasCount(0);
        assertThat(page.locator("#form-paid")).hasCount(0);
        assertThat(page.locator("#form-cancel")).hasCount(0);
        assertThat(page.locator("[data-testid='payment-form-section']")).hasCount(0);
        Bill paid = billService.findById(bill.getId());
        assertEquals(BillStatus.PAID, paid.getStatus());
        assertNotNull(paid.getPaidAt());
    }

    @Test
    @DisplayName("Should cancel an approved bill, then delete it; an approved bill cannot be deleted")
    void shouldCancelAndDeleteBill() {
        Vendor vendor = createVendor();
        Bill bill = createBill(vendor, null, LocalDate.of(YEAR, 11, 1), line("Batal", CLOUD, "1", "300000", null));
        asAdmin(() -> billService.approve(bill.getId()));

        navigateTo("/bills/" + bill.getBillNumber());
        waitForPageLoad();
        assertThat(page.locator("#form-delete")).hasCount(0);
        String csrf = page.locator("meta[name='_csrf']").getAttribute("content");
        page.request().post(baseUrl() + "/bills/" + bill.getBillNumber() + "/delete",
                RequestOptions.create().setHeader("X-CSRF-TOKEN", csrf));
        navigateTo("/bills/" + bill.getBillNumber());
        waitForPageLoad();
        assertThat(page.locator("[data-testid='bill-status']")).hasText("Disetujui");
        assertTrue(billRepository.existsByBillNumber(bill.getBillNumber()));

        page.onceDialog(dialog -> dialog.accept());
        page.locator("#form-cancel button").click();
        waitForPageLoad();

        assertThat(page.locator("[data-testid='flash-success']")).hasText("Tagihan dibatalkan");
        assertThat(page.locator("[data-testid='bill-status']")).hasText("Dibatalkan");
        assertThat(page.locator("#form-cancel")).hasCount(0);
        assertThat(page.locator("#form-paid")).hasCount(0);
        assertEquals(BillStatus.CANCELLED, billService.findById(bill.getId()).getStatus());

        page.onceDialog(dialog -> dialog.accept());
        page.locator("#form-delete button").click();
        waitForPageLoad();

        assertEquals(baseUrl() + "/bills", page.url());
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Tagihan berhasil dihapus");
        assertFalse(billRepository.existsByBillNumber(bill.getBillNumber()));
    }

    private Vendor createVendor() {
        Vendor vendor = new Vendor();
        vendor.setCode("BV-" + shortId());
        vendor.setName("PT Pemasok Tagihan " + shortId());
        return vendorService.create(vendor);
    }

    private BillLine line(String description, String accountCode, String quantity, String unitPrice, String taxRate) {
        BillLine line = new BillLine();
        line.setDescription(description);
        if (accountCode != null) {
            line.setExpenseAccount(account(accountCode));
        }
        line.setQuantity(new BigDecimal(quantity));
        line.setUnitPrice(new BigDecimal(unitPrice));
        line.setTaxRate(taxRate == null ? null : new BigDecimal(taxRate));
        return line;
    }

    private Bill createBill(Vendor vendor, String vendorInvoiceNumber, LocalDate billDate, BillLine... lines) {
        Bill bill = new Bill();
        bill.setVendor(vendor);
        bill.setVendorInvoiceNumber(vendorInvoiceNumber);
        bill.setBillDate(billDate);
        bill.setDueDate(billDate.plusDays(30));
        return billService.create(bill, List.of(lines));
    }

    private ChartOfAccount account(String code) {
        return chartOfAccountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + code));
    }

    private void asAdmin(Runnable action) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null, Collections.emptyList()));
        try {
            action.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void selectVendor(Vendor vendor) {
        page.locator("#vendorLabel").fill(vendor.getCode());
        Locator match = page.locator("[data-testid='vendor-picker-result']").filter(
                new Locator.FilterOptions().setHasText(vendor.getName()));
        assertThat(match).hasCount(1);
        match.click();
        assertThat(page.locator("#vendorLabel")).hasValue(vendor.getCode() + " - " + vendor.getName());
        assertThat(page.locator("input[name='vendor.id']")).hasValue(vendor.getId().toString());
    }

    private void fillLine(int index, String description, String accountCode, String quantity, String unitPrice, String taxRate) {
        page.locator("#bill-lineDescription-" + index).fill(description);
        Locator label = page.locator("#bill-lineExpenseAccountLabel-" + index);
        label.fill(accountCode);
        Locator match = page.locator("[data-testid='line-account-picker-result']").filter(
                new Locator.FilterOptions().setHasText(Pattern.compile("^\\s*" + accountCode.replace(".", "\\.") + "\\s")));
        assertThat(match).hasCount(1);
        match.click();
        assertThat(label).hasValue(Pattern.compile("^" + accountCode.replace(".", "\\.") + " - "));
        page.locator("#bill-lineQuantity-" + index).fill(quantity);
        page.locator("#bill-lineUnitPrice-" + index).fill(unitPrice);
        page.locator("#bill-lineTaxRate-" + index).fill(taxRate);
    }

    private List<String> billLines(Bill bill) {
        return jdbcTemplate.queryForList("""
                SELECT bl.line_order || '|' || bl.description || '|' || COALESCE(coa.account_code, 'null') || '|'
                       || bl.quantity || '|' || bl.unit_price || '|' || COALESCE(bl.tax_rate::text, 'null') || '|'
                       || bl.amount || '|' || bl.tax_amount
                FROM bill_lines bl LEFT JOIN chart_of_accounts coa ON coa.id = bl.id_expense_account
                WHERE bl.id_bill = ? ORDER BY bl.line_order""", String.class, bill.getId());
    }

    private List<String> billTransactions(Bill bill) {
        return jdbcTemplate.queryForList("""
                SELECT t.status || '|' || t.transaction_date || '|' || t.amount || '|' || t.description || '|' || jt.template_name
                FROM transactions t JOIN journal_templates jt ON jt.id = t.id_journal_template
                WHERE t.source_document_type = 'BILL' AND t.source_document_id = ?
                ORDER BY t.description""", String.class, bill.getId());
    }

    private List<UUID> billTransactionIds(Bill bill) {
        return jdbcTemplate.queryForList("""
                SELECT t.id FROM transactions t
                WHERE t.source_document_type = 'BILL' AND t.source_document_id = ?
                ORDER BY t.description""", UUID.class, bill.getId());
    }

    private List<String> journalLines(UUID transactionId) {
        return jdbcTemplate.queryForList("""
                SELECT CASE WHEN je.debit_amount > 0 THEN 'DEBIT:' || coa.account_code || ':' || je.debit_amount
                            ELSE 'CREDIT:' || coa.account_code || ':' || je.credit_amount END
                FROM journal_entries je JOIN chart_of_accounts coa ON coa.id = je.id_account
                WHERE je.id_transaction = ? ORDER BY je.debit_amount = 0, coa.account_code""", String.class, transactionId);
    }

    private Locator billRows() {
        return page.locator("[data-testid='bill-table'] tbody tr:has(td a)");
    }

    private Locator infoValue(String label) {
        return page.locator("[data-testid='bill-detail'] div:has(> p.text-gray-500:text-is('" + label + "')) > p.font-medium");
    }

    private Locator totalsValue(String label) {
        return page.locator("div.flex.justify-between:has(> span:text-is('" + label + "')) > span").nth(1);
    }

    private Locator errors() {
        return page.locator("form ul li");
    }

    private Locator form() {
        return page.locator("form:has(#btn-simpan)");
    }

    private void submit() {
        page.locator("#btn-simpan").click();
        waitForPageLoad();
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }
}
