package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.service.TaxDeadlineService;
import com.artivisi.accountingfinance.service.TaxDeadlineService.MonthlyChecklistSummary;
import com.artivisi.accountingfinance.service.TaxDeadlineService.TaxDeadlineStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/tax-calendar")
@RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasAuthority('" + com.artivisi.accountingfinance.security.Permission.TAX_CALENDAR_VIEW + "')")
public class TaxCalendarController {

    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";
    private static final String ATTR_ERROR_MESSAGE = "errorMessage";
    private static final String ATTR_CURRENT_PAGE = "currentPage";
    private static final String PAGE_TAX_CALENDAR = "tax-calendar";

    private final TaxDeadlineService taxDeadlineService;

    @GetMapping
    public String list(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            Model model) {

        LocalDate now = LocalDate.now();
        int taxPeriodMonth = now.getMonthValue() == 1 ? 12 : now.getMonthValue() - 1;
        int taxPeriodYear = now.getMonthValue() == 1 ? now.getYear() - 1 : now.getYear();

        int selectedYear = year != null ? year : taxPeriodYear;
        int selectedMonth = month != null ? month : taxPeriodMonth;

        MonthlyChecklistSummary summary = taxDeadlineService.getMonthlyChecklistSummary(selectedYear, selectedMonth);

        model.addAttribute("summary", summary);
        model.addAttribute("selectedYear", selectedYear);
        model.addAttribute("selectedMonth", selectedMonth);
        model.addAttribute("monthNames", getMonthNames());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_TAX_CALENDAR);

        if ("true".equals(hxRequest)) {
            return "tax-calendar/fragments/checklist :: checklist";
        }

        return "tax-calendar/list";
    }

    @GetMapping("/yearly")
    public String yearly(
            @RequestParam(required = false) Integer year,
            Model model) {

        int selectedYear = year != null ? year : LocalDate.now().getYear();

        List<MonthlyChecklistSummary> summaries = taxDeadlineService.getYearlyChecklistSummary(selectedYear);

        model.addAttribute("summaries", summaries);
        model.addAttribute("selectedYear", selectedYear);
        model.addAttribute("monthNames", getMonthNames());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_TAX_CALENDAR);

        return "tax-calendar/yearly";
    }

    @PostMapping("/complete/{deadlineId}")
    public String markAsCompleted(
            @PathVariable UUID deadlineId,
            @RequestParam Integer year,
            @RequestParam Integer month,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate completedDate,
            @RequestParam(required = false) String referenceNumber,
            @RequestParam(required = false) String notes,
            RedirectAttributes redirectAttributes) {

        try {
            taxDeadlineService.markAsCompleted(deadlineId, year, month, completedDate, referenceNumber, notes);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Kewajiban pajak berhasil ditandai selesai");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
        }

        return "redirect:/tax-calendar?year=" + year + "&month=" + month;
    }

    @PostMapping("/uncomplete/{completionId}")
    public String removeCompletion(
            @PathVariable UUID completionId,
            @RequestParam Integer year,
            @RequestParam Integer month,
            RedirectAttributes redirectAttributes) {

        try {
            taxDeadlineService.removeCompletion(completionId);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Status selesai berhasil dihapus");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
        }

        return "redirect:/tax-calendar?year=" + year + "&month=" + month;
    }

    @GetMapping("/upcoming")
    public String upcoming(Model model) {
        List<TaxDeadlineStatus> upcoming = taxDeadlineService.getUpcomingDeadlines();
        List<TaxDeadlineStatus> overdue = taxDeadlineService.getOverdueDeadlines();
        List<TaxDeadlineStatus> dueSoon = taxDeadlineService.getDueSoonDeadlines();

        model.addAttribute("upcoming", upcoming);
        model.addAttribute("overdue", overdue);
        model.addAttribute("dueSoon", dueSoon);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_TAX_CALENDAR);

        return "tax-calendar/upcoming";
    }

    @GetMapping("/api/widget")
    public String dashboardWidget(Model model) {
        List<TaxDeadlineStatus> overdue = taxDeadlineService.getOverdueDeadlines();
        List<TaxDeadlineStatus> dueSoon = taxDeadlineService.getDueSoonDeadlines();

        model.addAttribute("overdue", overdue);
        model.addAttribute("dueSoon", dueSoon);
        model.addAttribute("overdueCount", overdue.size());
        model.addAttribute("dueSoonCount", dueSoon.size());

        return "tax-calendar/fragments/widget :: widget";
    }

    private String[] getMonthNames() {
        return new String[]{"", "Januari", "Februari", "Maret", "April", "Mei", "Juni",
                "Juli", "Agustus", "September", "Oktober", "November", "Desember"};
    }
}
