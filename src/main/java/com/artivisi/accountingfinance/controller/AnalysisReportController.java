package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.AnalysisReport;
import com.artivisi.accountingfinance.repository.AnalysisReportRepository;
import com.artivisi.accountingfinance.security.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

import static com.artivisi.accountingfinance.controller.ViewConstants.*;

@Controller
@RequestMapping("/analysis-reports")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permission.ANALYSIS_REPORT_VIEW + "')")
public class AnalysisReportController {

    private final AnalysisReportRepository analysisReportRepository;

    @GetMapping
    public String list(Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_ANALYSIS_REPORTS);
        model.addAttribute("reports", analysisReportRepository.findAllByOrderByCreatedAtDesc());
        return "analysis-reports/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        AnalysisReport report = analysisReportRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Laporan analisis tidak ditemukan: " + id));
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_ANALYSIS_REPORTS);
        model.addAttribute("report", report);
        return "analysis-reports/detail";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('" + Permission.ANALYSIS_REPORT_DELETE + "')")
    public String delete(@PathVariable UUID id, Authentication authentication, RedirectAttributes redirectAttributes) {
        AnalysisReport report = analysisReportRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Laporan analisis tidak ditemukan: " + id));

        String username = authentication != null ? authentication.getName() : "system";
        report.setUpdatedBy(username);
        report.softDelete();
        analysisReportRepository.save(report);

        redirectAttributes.addFlashAttribute("successMessage", "Laporan analisis berhasil dihapus");
        return "redirect:/analysis-reports";
    }
}
