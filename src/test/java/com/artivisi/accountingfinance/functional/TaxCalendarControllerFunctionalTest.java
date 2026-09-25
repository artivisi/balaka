package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.TaxDeadline;
import com.artivisi.accountingfinance.entity.TaxDeadlineCompletion;
import com.artivisi.accountingfinance.enums.TaxDeadlineType;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.TaxDeadlineCompletionRepository;
import com.artivisi.accountingfinance.repository.TaxDeadlineRepository;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tax calendar for the eight seeded deadlines (industry-seed/it-service 12_tax_deadlines.csv).
 * Completions are recorded only in {@link #YEAR}, reserved for this class; each test uses its own month.
 */
@DisplayName("Tax Calendar Controller Tests")
@Import(ServiceTestDataInitializer.class)
class TaxCalendarControllerFunctionalTest extends PlaywrightTestBase {

    private static final int YEAR = 2067;
    private static final String[] MONTHS = {"", "Januari", "Februari", "Maret", "April", "Mei", "Juni", "Juli",
            "Agustus", "September", "Oktober", "November", "Desember"};
    private static final Map<TaxDeadlineType, String> NAMES = Map.of(
            TaxDeadlineType.PPH_21_PAYMENT, "Setor PPh 21",
            TaxDeadlineType.PPH_23_PAYMENT, "Setor PPh 23",
            TaxDeadlineType.PPH_42_PAYMENT, "Setor PPh 4(2)",
            TaxDeadlineType.PPH_25_PAYMENT, "Setor PPh 25",
            TaxDeadlineType.PPN_PAYMENT, "Setor PPN",
            TaxDeadlineType.SPT_PPH_21, "Lapor SPT PPh 21",
            TaxDeadlineType.SPT_PPH_23, "Lapor SPT PPh 23",
            TaxDeadlineType.SPT_PPN, "Lapor SPT PPN");

    @Autowired private TaxDeadlineRepository deadlineRepository;
    @Autowired private TaxDeadlineCompletionRepository completionRepository;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
    }

    @Test
    @DisplayName("Monthly checklist lists the eight deadlines due the 15th, 20th and last day of the following month")
    void shouldShowDeadlinesForPeriod() {
        openPeriod(YEAR, 1);

        assertThat(page.locator("#page-title")).hasText("Kalender Pajak");
        assertThat(page.locator("[data-testid='checklist-title']")).hasText("Checklist Kewajiban Pajak - Januari " + YEAR);
        assertThat(page.locator("#month-selector")).hasValue("1");
        assertThat(page.locator("#year-selector")).hasValue(String.valueOf(YEAR));
        assertThat(page.locator("[data-testid='total-count']")).hasText("8");
        assertThat(page.locator("[data-testid='completed-count']")).hasText("0");
        assertThat(page.locator("[data-testid='overdue-count']")).hasText("0");
        assertThat(page.locator("[data-testid='due-soon-count']")).hasText("0");
        assertThat(page.locator(".deadline-row")).hasCount(8);

        for (TaxDeadlineType type : TaxDeadlineType.values()) {
            Locator row = row(type);
            assertThat(row).containsText(NAMES.get(type));
            assertThat(row.locator(".due-date")).hasText(formatDate(expectedDueDate(type, YEAR, 1)));
            assertThat(row.locator(".btn-mark-complete")).hasText("Tandai Selesai");
        }
        assertThat(row(TaxDeadlineType.PPH_21_PAYMENT).locator(".due-date")).hasText("15 Februari " + YEAR);
        assertThat(row(TaxDeadlineType.SPT_PPH_23).locator(".due-date")).hasText("20 Februari " + YEAR);
        assertThat(row(TaxDeadlineType.SPT_PPN).locator(".due-date")).hasText("28 Februari " + YEAR);
        assertThat(page.locator(".deadline-row").first().locator(".due-date")).hasText("15 Februari " + YEAR);
        assertThat(page.locator(".deadline-row").last().locator(".due-date")).hasText("28 Februari " + YEAR);
    }

    @Test
    @DisplayName("Due dates roll into the next year for December and end on 29 February in a leap year")
    void shouldRollDueDatesAcrossYearAndLeapFebruary() {
        openPeriod(YEAR, 12);
        assertThat(row(TaxDeadlineType.PPH_25_PAYMENT).locator(".due-date")).hasText("15 Januari " + (YEAR + 1));
        assertThat(row(TaxDeadlineType.SPT_PPH_21).locator(".due-date")).hasText("20 Januari " + (YEAR + 1));
        assertThat(row(TaxDeadlineType.PPN_PAYMENT).locator(".due-date")).hasText("31 Januari " + (YEAR + 1));

        openPeriod(YEAR + 1, 1);
        assertThat(row(TaxDeadlineType.PPN_PAYMENT).locator(".due-date")).hasText("29 Februari " + (YEAR + 1));
        assertThat(row(TaxDeadlineType.SPT_PPN).locator(".due-date")).hasText("29 Februari " + (YEAR + 1));
    }

    @Test
    @DisplayName("Uncompleted deadlines of a past period are all overdue with the number of days late")
    void shouldMarkPastPeriodOverdue() {
        openPeriod(2021, 7);

        assertThat(page.locator("[data-testid='overdue-count']")).hasText("8");
        assertThat(page.locator("[data-testid='completed-count']")).hasText("0");
        assertThat(page.locator("[data-testid='due-soon-count']")).hasText("0");
        LocalDate today = LocalDate.now();
        for (TaxDeadlineType type : TaxDeadlineType.values()) {
            long late = ChronoUnit.DAYS.between(expectedDueDate(type, 2021, 7), today);
            assertThat(row(type).locator(".days-left")).hasText("Terlambat " + late + " hari");
        }
    }

    @Test
    @DisplayName("Changing the month or year selector reloads the checklist and counters for that period")
    void shouldReloadPeriodFromSelectors() {
        openPeriod(YEAR, 1);

        page.locator("#month-selector").selectOption("3");
        page.waitForURL("**/tax-calendar?year=" + YEAR + "&month=3");
        assertThat(page.locator("[data-testid='checklist-title']")).hasText("Checklist Kewajiban Pajak - Maret " + YEAR);
        assertThat(row(TaxDeadlineType.PPH_21_PAYMENT).locator(".due-date")).hasText("15 April " + YEAR);

        page.locator("#year-selector").selectOption(String.valueOf(YEAR + 1));
        page.waitForURL("**/tax-calendar?year=" + (YEAR + 1) + "&month=3");
        assertThat(page.locator("[data-testid='checklist-title']")).hasText("Checklist Kewajiban Pajak - Maret " + (YEAR + 1));
        assertThat(page.locator("#year-selector option")).hasCount(11);
        assertThat(page.locator("#year-selector option").first()).hasText(String.valueOf(YEAR + 1 - 5));
    }

    @Test
    @DisplayName("Marking a deadline complete through the modal persists date, reference and notes for that period only")
    void shouldMarkDeadlineComplete() {
        TaxDeadline pph23 = deadline(TaxDeadlineType.PPH_23_PAYMENT);
        String reference = "NTPN-" + UUID.randomUUID().toString().substring(0, 12);
        openPeriod(YEAR, 2);

        assertThat(page.locator("[data-testid='complete-modal']")).isHidden();
        row(TaxDeadlineType.PPH_23_PAYMENT).locator(".btn-mark-complete").click();
        assertThat(page.locator("[data-testid='complete-modal']")).isVisible();
        page.locator("#completedDate").fill(YEAR + "-03-10");
        page.locator("#referenceNumber").fill(reference);
        page.locator("#notes").fill("Setor lewat bank persepsi");
        page.locator("#btn-modal-save").click();
        page.waitForURL("**/tax-calendar?year=" + YEAR + "&month=2");

        assertThat(page.locator("[data-testid='flash-success']")).hasText("Kewajiban pajak berhasil ditandai selesai");
        assertThat(page.locator("[data-testid='completed-count']")).hasText("1");
        Locator row = row(TaxDeadlineType.PPH_23_PAYMENT);
        assertThat(row.locator(".completed-date")).hasText("10 Maret " + YEAR);
        assertThat(row.locator(".reference-number")).hasText(reference);
        assertThat(row.locator(".btn-mark-complete")).hasCount(0);
        assertThat(row.locator("form.form-uncomplete button")).hasText("Batalkan");

        TaxDeadlineCompletion completion = completionRepository.findByDeadlineIdAndYearAndMonth(pph23.getId(), YEAR, 2)
                .orElseThrow(() -> new AssertionError("Completion was not saved"));
        assertThat(completion.getCompletedDate()).isEqualTo(LocalDate.of(YEAR, 3, 10));
        assertThat(completion.getReferenceNumber()).isEqualTo(reference);
        assertThat(completion.getNotes()).isEqualTo("Setor lewat bank persepsi");
        assertThat(completion.getCompletedBy()).isEqualTo("admin");
        assertThat(completionRepository.findByYearAndMonth(YEAR, 2)).hasSize(1);
        assertThat(completionRepository.findByDeadlineIdAndYearAndMonth(pph23.getId(), YEAR, 3)).isEmpty();
    }

    @Test
    @DisplayName("Completing the same deadline twice for a period is rejected and keeps the first completion")
    void shouldRejectDuplicateCompletion() {
        TaxDeadline sptPpn = deadline(TaxDeadlineType.SPT_PPN);
        openPeriod(YEAR, 4);

        assertThat(postComplete(sptPpn, 4, YEAR + "-05-20", "BPE-FIRST").status()).isEqualTo(302);
        openPeriod(YEAR, 4);
        assertThat(page.locator("[data-testid='flash-success']")).hasText("Kewajiban pajak berhasil ditandai selesai");
        APIResponse second = postComplete(sptPpn, 4, YEAR + "-05-25", "BPE-SECOND");
        assertThat(second.status()).isEqualTo(302);
        assertThat(second.headers().get("location")).endsWith("/tax-calendar?year=" + YEAR + "&month=4");

        openPeriod(YEAR, 4);
        assertThat(page.locator("[data-testid='flash-success']")).hasCount(0);
        assertThat(page.locator("[data-testid='flash-error']"))
                .hasText("Kewajiban pajak sudah ditandai selesai untuk periode ini");
        List<TaxDeadlineCompletion> completions = completionRepository.findByYearAndMonth(YEAR, 4);
        assertThat(completions).hasSize(1);
        assertThat(completions.getFirst().getReferenceNumber()).isEqualTo("BPE-FIRST");
        assertThat(completions.getFirst().getCompletedDate()).isEqualTo(LocalDate.of(YEAR, 5, 20));
    }

    @Test
    @DisplayName("Cancelling a completion deletes it and makes the deadline pending again")
    void shouldRemoveCompletion() {
        TaxDeadline pph21 = deadline(TaxDeadlineType.PPH_21_PAYMENT);
        openPeriod(YEAR, 5);
        assertThat(postComplete(pph21, 5, YEAR + "-06-14", "NTPN-CANCEL").status()).isEqualTo(302);
        openPeriod(YEAR, 5);
        assertThat(page.locator("[data-testid='completed-count']")).hasText("1");

        page.onceDialog(dialog -> {
            assertThat(dialog.message()).isEqualTo("Yakin ingin menghapus status selesai?");
            dialog.accept();
        });
        row(TaxDeadlineType.PPH_21_PAYMENT).locator("form.form-uncomplete button").click();
        page.waitForURL("**/tax-calendar?year=" + YEAR + "&month=5");

        assertThat(page.locator("[data-testid='flash-success']")).hasText("Status selesai berhasil dihapus");
        assertThat(page.locator("[data-testid='completed-count']")).hasText("0");
        assertThat(row(TaxDeadlineType.PPH_21_PAYMENT).locator(".btn-mark-complete")).hasText("Tandai Selesai");
        assertThat(completionRepository.findByDeadlineIdAndYearAndMonth(pph21.getId(), YEAR, 5)).isEmpty();
    }

    @Test
    @DisplayName("Yearly view shows per-month completion counts and links each month to its checklist")
    void shouldShowYearlySummary() {
        openPeriod(YEAR, 6);
        assertThat(postComplete(deadline(TaxDeadlineType.PPN_PAYMENT), 6, YEAR + "-07-30", null).status()).isEqualTo(302);
        assertThat(postComplete(deadline(TaxDeadlineType.SPT_PPN), 6, YEAR + "-07-31", null).status()).isEqualTo(302);

        openPeriod(YEAR, 6);
        page.locator("a[href='/tax-calendar/yearly?year=" + YEAR + "']").click();
        page.waitForURL("**/tax-calendar/yearly?year=" + YEAR);

        assertThat(page.locator("#page-title")).hasText("Kalender Pajak Tahunan");
        assertThat(page.locator("#year-select")).hasValue(String.valueOf(YEAR));
        assertThat(page.locator("[data-testid^='month-card-']")).hasCount(12);
        Locator june = page.locator("[data-testid='month-card-6']");
        assertThat(june).containsText("Juni");
        assertThat(june.locator(".completion-count")).hasText("2/8");
        assertThat(june.locator(".completion-percent")).hasText("25%");
        Locator november = page.locator("[data-testid='month-card-11']");
        assertThat(november.locator(".completion-count")).hasText("0/8");
        assertThat(november.locator(".completion-percent")).hasText("0%");

        june.locator("a").click();
        page.waitForURL("**/tax-calendar?year=" + YEAR + "&month=6");
        assertThat(page.locator("[data-testid='completed-count']")).hasText("2");

        navigateTo("/tax-calendar/yearly?year=" + YEAR);
        page.locator("#year-select").selectOption(String.valueOf(YEAR - 1));
        page.waitForURL("**/tax-calendar/yearly?year=" + (YEAR - 1));
        assertThat(page.locator("[data-testid='month-card-6'] .completion-count")).hasText("0/8");

        page.locator("a[href='/tax-calendar']").filter(new Locator.FilterOptions().setHasText("Kembali")).click();
        page.waitForURL("**/tax-calendar");
        assertThat(page.locator("[data-testid='checklist-title']")).hasText(
                "Checklist Kewajiban Pajak - " + MONTHS[currentTaxPeriod().getMonthValue()] + " " + currentTaxPeriod().getYear());
    }

    @Test
    @DisplayName("Upcoming page splits the current period's open deadlines into overdue, due soon and later, each once")
    void shouldShowUpcomingDeadlinesByStatus() {
        navigateTo("/tax-calendar/upcoming");
        waitForPageLoad();
        assertThat(page.locator("#page-title")).hasText("Deadline Pajak Mendatang");

        Expected expected = expectedForCurrentPeriod();
        assertSection("overdue-section", expected.overdue());
        assertSection("due-soon-section", expected.dueSoon());
        assertSection("upcoming-section", expected.later());
        assertThat(page.locator(".deadline-item")).hasCount(
                expected.overdue().size() + expected.dueSoon().size() + expected.later().size());
    }

    @Test
    @DisplayName("Dashboard widget lists the current period's overdue and due-soon deadlines with day counts")
    void shouldRenderDashboardWidget() {
        navigateTo("/tax-calendar/api/widget");
        waitForPageLoad();

        Expected expected = expectedForCurrentPeriod();
        Locator widget = page.locator("body");
        assertThat(widget).containsText("Kewajiban Pajak");
        if (expected.overdue().isEmpty() && expected.dueSoon().isEmpty()) {
            assertThat(widget).containsText("Semua kewajiban pajak sudah selesai");
            return;
        }
        LocalDate today = LocalDate.now();
        YearMonth period = currentTaxPeriod();
        if (!expected.overdue().isEmpty()) {
            assertThat(widget).containsText("Terlambat (" + expected.overdue().size() + ")");
        }
        if (!expected.dueSoon().isEmpty()) {
            assertThat(widget).containsText("Segera Jatuh Tempo (" + expected.dueSoon().size() + ")");
        }
        for (TaxDeadlineType type : expected.overdue()) {
            long late = ChronoUnit.DAYS.between(expectedDueDate(type, period.getYear(), period.getMonthValue()), today);
            assertThat(widgetItem(type)).containsText("Terlambat " + late + " hari");
        }
        for (TaxDeadlineType type : expected.dueSoon()) {
            long left = ChronoUnit.DAYS.between(today, expectedDueDate(type, period.getYear(), period.getMonthValue()));
            assertThat(widgetItem(type)).containsText(left + " hari lagi");
        }
        assertThat(widget).not().containsText("Semua kewajiban pajak sudah selesai");
    }

    private record Expected(List<TaxDeadlineType> overdue, List<TaxDeadlineType> dueSoon, List<TaxDeadlineType> later) {}

    private Expected expectedForCurrentPeriod() {
        YearMonth period = currentTaxPeriod();
        LocalDate today = LocalDate.now();
        List<TaxDeadlineType> overdue = new ArrayList<>();
        List<TaxDeadlineType> dueSoon = new ArrayList<>();
        List<TaxDeadlineType> later = new ArrayList<>();
        for (TaxDeadlineType type : TaxDeadlineType.values()) {
            if (completionRepository.findByDeadlineIdAndYearAndMonth(deadline(type).getId(),
                    period.getYear(), period.getMonthValue()).isPresent()) {
                continue;
            }
            LocalDate due = expectedDueDate(type, period.getYear(), period.getMonthValue());
            if (today.isAfter(due)) {
                overdue.add(type);
            } else if (!today.isBefore(due.minusDays(7))) {
                dueSoon.add(type);
            } else {
                later.add(type);
            }
        }
        return new Expected(overdue, dueSoon, later);
    }

    private void assertSection(String testId, List<TaxDeadlineType> types) {
        Locator section = page.locator("[data-testid='" + testId + "']");
        if (types.isEmpty()) {
            assertThat(section).hasCount(0);
            return;
        }
        YearMonth period = currentTaxPeriod();
        assertThat(section.locator(".deadline-item")).hasCount(types.size());
        for (TaxDeadlineType type : types) {
            Locator item = section.locator(".deadline-item").filter(new Locator.FilterOptions()
                    .setHas(page.locator("span.font-medium").getByText(NAMES.get(type),
                            new Locator.GetByTextOptions().setExact(true))));
            assertThat(item).hasCount(1);
            LocalDate due = expectedDueDate(type, period.getYear(), period.getMonthValue());
            assertThat(item).containsText(String.format("%02d", due.getDayOfMonth()));
        }
    }

    private Locator widgetItem(TaxDeadlineType type) {
        return page.locator("div.flex.items-center.justify-between.text-sm").filter(new Locator.FilterOptions()
                .setHas(page.getByText(NAMES.get(type), new Page.GetByTextOptions().setExact(true))));
    }

    private static YearMonth currentTaxPeriod() {
        return YearMonth.now().minusMonths(1);
    }

    private static LocalDate expectedDueDate(TaxDeadlineType type, int year, int month) {
        YearMonth next = YearMonth.of(year, month).plusMonths(1);
        return switch (type) {
            case PPH_21_PAYMENT, PPH_23_PAYMENT, PPH_42_PAYMENT, PPH_25_PAYMENT -> next.atDay(15);
            case SPT_PPH_21, SPT_PPH_23 -> next.atDay(20);
            case PPN_PAYMENT, SPT_PPN -> next.atEndOfMonth();
        };
    }

    private static String formatDate(LocalDate date) {
        return date.getDayOfMonth() + " " + MONTHS[date.getMonthValue()] + " " + date.getYear();
    }

    private void openPeriod(int year, int month) {
        navigateTo("/tax-calendar?year=" + year + "&month=" + month);
        waitForPageLoad();
        assertThat(page.locator("[data-testid='checklist-title']"))
                .hasText("Checklist Kewajiban Pajak - " + MONTHS[month] + " " + year);
    }

    private Locator row(TaxDeadlineType type) {
        return page.locator("[data-testid='deadline-" + type.name() + "']");
    }

    private TaxDeadline deadline(TaxDeadlineType type) {
        return deadlineRepository.findByDeadlineType(type)
                .orElseThrow(() -> new IllegalStateException("Seeded tax deadline missing: " + type));
    }

    private APIResponse postComplete(TaxDeadline deadline, int month, String completedDate, String reference) {
        String csrf = page.locator("meta[name='_csrf']").getAttribute("content");
        FormData form = FormData.create()
                .set("_csrf", csrf)
                .set("year", String.valueOf(YEAR))
                .set("month", String.valueOf(month))
                .set("completedDate", completedDate);
        if (reference != null) {
            form.set("referenceNumber", reference);
        }
        return page.request().post(baseUrl() + "/tax-calendar/complete/" + deadline.getId(),
                RequestOptions.create().setMaxRedirects(0).setForm(form));
    }
}
