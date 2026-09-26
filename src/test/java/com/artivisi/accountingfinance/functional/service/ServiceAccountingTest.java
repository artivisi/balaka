package com.artivisi.accountingfinance.functional.service;

import com.artivisi.accountingfinance.entity.ChartOfAccount;
import com.artivisi.accountingfinance.entity.JournalEntry;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalEntryRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Service industry entry points: login, dashboard KPIs, and choosing a template for a new transaction.
 *
 * The KPI test posts its own journals into {@link #KPI_MONTH} and the month before it, a period no
 * other test posts into, so the month's figures are exact.
 */
@DisplayName("Service Industry - Accounting")
@Import(ServiceTestDataInitializer.class)
class ServiceAccountingTest extends PlaywrightTestBase {

    private static final YearMonth KPI_MONTH = YearMonth.of(2096, 3);

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private JournalEntryRepository journalEntryRepository;
    @Autowired private JournalTemplateRepository templateRepository;
    @Autowired private ChartOfAccountRepository accountRepository;

    @Test
    @DisplayName("Should redirect to the login form and land on the dashboard with the current month's KPIs after login")
    void shouldLogInThroughFormToDashboard() {
        navigateTo("/dashboard");
        waitForPageLoad();
        assertThat(page).hasURL(baseUrl() + "/login");
        assertThat(page.locator("#username")).hasAttribute("placeholder", "Masukkan username");
        assertThat(page.locator("#password")).hasAttribute("type", "password");
        assertThat(page.locator("#btn-login")).hasAttribute("type", "submit");
        takeManualScreenshot("login");

        page.locator("#username").fill("admin");
        page.locator("#password").fill("admin");
        page.locator("#btn-login").click();
        page.waitForURL(Pattern.compile(".*/dashboard(\\?continue)?$"));
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Dashboard");
        YearMonth now = YearMonth.now();
        assertThat(page.locator("#kpi-period")).hasText(now.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.of("id", "ID"))));
        assertThat(page.locator("#month-selector")).hasValue(now.toString());
        takeManualScreenshot("dashboard");
    }

    @Test
    @DisplayName("Should show revenue, expense, net profit, margin, their change from last month and the transaction count for the selected month")
    void shouldShowKpisOfSelectedMonth() {
        LocalDate start = KPI_MONTH.minusMonths(1).atDay(1);
        LocalDate end = KPI_MONTH.atEndOfMonth();
        assertEquals(0, transactionRepository.findPostedTransactionsBetweenDates(start, end).size(),
                "Another test posted into reserved dashboard period " + start + " - " + end);

        String run = UUID.randomUUID().toString().substring(0, 6);
        // Previous month: revenue 10,000,000, no expense (margin 100%)
        post("KPI-" + run + "-1", KPI_MONTH.minusMonths(1).atDay(10), "Pendapatan Jasa Konsultasi",
                "1.1.02", "4.1.02", "10000000");
        // Selected month: revenue 12,500,000, expense 2,300,000
        post("KPI-" + run + "-2", KPI_MONTH.atDay(5), "Pendapatan Jasa Konsultasi",
                "1.1.02", "4.1.02", "12500000");
        post("KPI-" + run + "-3", KPI_MONTH.atDay(20), "Bayar Beban Gaji",
                "5.1.01", "1.1.02", "2300000");

        loginAsAdmin();
        assertThat(page.locator("#kpi-period")).isVisible();
        // htmx attaches the selector's change trigger in the settle phase after the swap;
        // a change fired before the container leaves htmx-settling is never sent.
        assertThat(page.locator("#dashboard-kpis-container")).not().hasClass(Pattern.compile(".*\\bhtmx-settling\\b.*"));
        page.waitForResponse(response -> response.url().endsWith("/dashboard/kpis?month=" + KPI_MONTH), () -> {
            page.locator("#month-selector").fill(KPI_MONTH.toString());
            page.locator("#month-selector").dispatchEvent("change");
        });

        assertThat(page.locator("#kpi-period")).hasText("Maret 2096");
        assertThat(page.locator("#month-selector")).hasValue(KPI_MONTH.toString());
        assertKpi("Pendapatan", "Rp 12,5jt", "+25,0%");
        assertKpi("Pengeluaran", "Rp 2,3jt", "+100,0%");
        assertKpi("Laba Bersih", "Rp 10,2jt", "+2,0%");
        assertKpi("Margin Laba", "81,6%", "-18,4pts");
        assertThat(kpiValue("Transaksi")).hasText("2");
        takeManualScreenshot("service/dashboard");
    }

    @Test
    @DisplayName("Should list the journal templates to choose from on a new transaction and open the form for the chosen one")
    void shouldChooseTemplateForNewTransaction() {
        JournalTemplate consulting = template("Pendapatan Jasa Konsultasi");
        loginAsAdmin();

        navigateTo("/transactions/new");
        waitForPageLoad();

        assertThat(page.locator("#page-title")).hasText("Transaksi Baru");
        assertThat(page.getByText("Pilih Template Jurnal")).isVisible();
        Locator option = page.locator("[data-testid='template-option'][href='/transactions/new?templateId="
                + consulting.getId() + "']");
        assertThat(option).hasCount(1);
        assertThat(option.locator(".font-medium")).hasText("Pendapatan Jasa Konsultasi");
        assertThat(option.locator(".bg-gray-50 > div")).hasText(new String[] {
            "D: (BANK)", "K: Pendapatan Jasa Konsultasi"});
        assertThat(page.locator("#btn-simpan-posting")).hasCount(0);
        takeManualScreenshot("transactions-form");

        option.click();
        waitForPageLoad();

        assertThat(page).hasURL(baseUrl() + "/transactions/new?templateId=" + consulting.getId());
        assertThat(page.getByText("Pilih Template Jurnal")).hasCount(0);
        assertThat(page.locator("[data-testid='template-option']")).hasCount(0);
        assertThat(page.locator("#amount")).isEditable();
        assertThat(page.locator("#btn-simpan-posting")).isVisible();
    }

    private void post(String number, LocalDate date, String templateName, String debitCode, String creditCode,
                      String amount) {
        BigDecimal value = new BigDecimal(amount);
        Transaction tx = new Transaction();
        tx.setTransactionNumber(number);
        tx.setTransactionDate(date);
        tx.setDescription("Dashboard KPI " + number);
        tx.setAmount(value);
        tx.setJournalTemplate(template(templateName));
        tx.setStatus(TransactionStatus.POSTED);
        tx.setPostedAt(LocalDateTime.now());
        tx.setPostedBy("admin");
        tx.setCreatedBy("admin");
        tx = transactionRepository.save(tx);
        entry(tx, account(debitCode), value, BigDecimal.ZERO, number + "-01");
        entry(tx, account(creditCode), BigDecimal.ZERO, value, number + "-02");
    }

    private void entry(Transaction tx, ChartOfAccount account, BigDecimal debit, BigDecimal credit, String journalNumber) {
        JournalEntry entry = new JournalEntry();
        entry.setJournalNumber(journalNumber);
        entry.setTransaction(tx);
        entry.setAccount(account);
        entry.setDebitAmount(debit);
        entry.setCreditAmount(credit);
        entry.setPostedAt(tx.getPostedAt());
        entry.setCreatedBy("admin");
        journalEntryRepository.save(entry);
    }

    private void assertKpi(String label, String value, String change) {
        assertThat(kpiValue(label)).hasText(value);
        assertThat(kpiValue(label).locator("xpath=following-sibling::p[1]")).hasText(change);
    }

    private Locator kpiValue(String label) {
        return page.locator("#dashboard-kpis p.text-xs.font-medium:text-is('" + label + "') + p");
    }

    private JournalTemplate template(String name) {
        return templateRepository.findByTemplateNameAndIsCurrentVersionTrue(name)
                .orElseThrow(() -> new IllegalStateException("Seeded template missing: " + name));
    }

    private ChartOfAccount account(String code) {
        return accountRepository.findByAccountCode(code)
                .orElseThrow(() -> new IllegalStateException("Seeded account missing: " + code));
    }
}
