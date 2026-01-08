package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.FiscalPeriod;
import com.artivisi.accountingfinance.enums.FiscalPeriodStatus;
import com.artivisi.accountingfinance.service.FiscalPeriodService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
import java.util.UUID;

@Controller
@RequestMapping("/fiscal-periods")
@RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasAuthority('" + com.artivisi.accountingfinance.security.Permission.FISCAL_PERIOD_VIEW + "')")
public class FiscalPeriodController {

    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";
    private static final String ATTR_ERROR_MESSAGE = "errorMessage";
    private static final String ATTR_CURRENT_PAGE = "currentPage";
    private static final String PAGE_FISCAL_PERIODS = "fiscal-periods";

    private final FiscalPeriodService fiscalPeriodService;

    @GetMapping
    public String list(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) FiscalPeriodStatus status,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            @PageableDefault(size = 12) Pageable pageable,
            Model model) {

        Page<FiscalPeriod> periods = fiscalPeriodService.findByFilters(year, status, pageable);

        model.addAttribute("periods", periods);
        model.addAttribute("year", year);
        model.addAttribute("status", status);
        model.addAttribute("statuses", FiscalPeriodStatus.values());
        model.addAttribute("years", fiscalPeriodService.findDistinctYears());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_FISCAL_PERIODS);

        if ("true".equals(hxRequest)) {
            return "fiscal-periods/fragments/period-table :: table";
        }

        return "fiscal-periods/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        int currentYear = LocalDate.now().getYear();
        model.addAttribute("currentYear", currentYear);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_FISCAL_PERIODS);
        return "fiscal-periods/form";
    }

    @PostMapping("/new")
    public String create(
            @RequestParam Integer year,
            @RequestParam Integer month,
            RedirectAttributes redirectAttributes) {

        try {
            FiscalPeriod saved = fiscalPeriodService.create(year, month);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Periode fiskal berhasil ditambahkan");
            return "redirect:/fiscal-periods/" + saved.getId();
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
            return "redirect:/fiscal-periods/new";
        }
    }

    @PostMapping("/generate-year")
    public String generateYear(
            @RequestParam Integer year,
            RedirectAttributes redirectAttributes) {

        int created = 0;
        for (int month = 1; month <= 12; month++) {
            try {
                fiscalPeriodService.create(year, month);
                created++;
            } catch (IllegalArgumentException e) {
                // Period already exists, skip
            }
        }

        if (created > 0) {
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE,
                    created + " periode fiskal berhasil ditambahkan untuk tahun " + year);
        } else {
            redirectAttributes.addFlashAttribute("infoMessage",
                    "Semua periode untuk tahun " + year + " sudah ada");
        }
        return "redirect:/fiscal-periods?year=" + year;
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        FiscalPeriod period = fiscalPeriodService.findById(id);
        model.addAttribute("period", period);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_FISCAL_PERIODS);
        return "fiscal-periods/detail";
    }

    @PostMapping("/{id}/close-month")
    public String closeMonth(
            @PathVariable UUID id,
            @RequestParam(required = false) String notes,
            RedirectAttributes redirectAttributes) {

        try {
            fiscalPeriodService.closeMonth(id, notes);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Bulan berhasil ditutup");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
        }
        return "redirect:/fiscal-periods/" + id;
    }

    @PostMapping("/{id}/file-tax")
    public String fileTax(
            @PathVariable UUID id,
            @RequestParam(required = false) String notes,
            RedirectAttributes redirectAttributes) {

        try {
            fiscalPeriodService.fileTax(id, notes);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "SPT berhasil dilaporkan");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
        }
        return "redirect:/fiscal-periods/" + id;
    }

    @PostMapping("/{id}/reopen")
    public String reopen(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason,
            RedirectAttributes redirectAttributes) {

        try {
            fiscalPeriodService.reopen(id, reason);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Periode berhasil dibuka kembali");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
        }
        return "redirect:/fiscal-periods/" + id;
    }
}
