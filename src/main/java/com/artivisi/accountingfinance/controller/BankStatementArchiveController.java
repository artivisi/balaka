package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.AlertRule;
import com.artivisi.accountingfinance.enums.AlertType;
import com.artivisi.accountingfinance.repository.AlertRuleRepository;
import com.artivisi.accountingfinance.repository.CompanyBankAccountRepository;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.BankStatementArchiveService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import static com.artivisi.accountingfinance.controller.ViewConstants.ATTR_CURRENT_PAGE;
import static com.artivisi.accountingfinance.controller.ViewConstants.PAGE_EVIDENCE;

/**
 * Monthly bank statement archive. The grace period after month end is the threshold of the
 * BANK_STATEMENT_MISSING alert rule, so the page and the alert agree.
 */
@Controller
@RequestMapping("/evidence/bank-statements")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permission.EVIDENCE_VIEW + "')")
public class BankStatementArchiveController {

    private static final String FLASH_SUCCESS = "successMessage";
    private static final String FLASH_ERROR = "errorMessage";
    private static final String REDIRECT = "redirect:/evidence/bank-statements?year=";

    private final BankStatementArchiveService archiveService;
    private final CompanyBankAccountRepository bankAccountRepository;
    private final AlertRuleRepository alertRuleRepository;

    @GetMapping
    public String grid(@RequestParam(required = false) Integer year, Model model) {
        int selected = year != null ? year : LocalDate.now().getYear();
        int graceDays = graceDays();
        model.addAttribute("year", selected);
        model.addAttribute("graceDays", graceDays);
        model.addAttribute("rows", archiveService.yearGrid(selected, LocalDate.now(), graceDays));
        model.addAttribute("archives", archiveService.archivesForYear(selected));
        model.addAttribute("bankAccounts", bankAccountRepository.findByActiveTrueOrderByBankNameAsc());
        model.addAttribute("retentionYears", archiveService.retentionYears());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_EVIDENCE);
        return "evidence/bank-statements";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permission.EVIDENCE_MANAGE + "')")
    public String upload(@RequestParam UUID bankAccountId,
                         @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                         @RequestParam("file") MultipartFile file,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        try {
            archiveService.archive(bankAccountId, month, file, authentication.getName(), LocalDate.now());
            redirectAttributes.addFlashAttribute(FLASH_SUCCESS, "Rekening koran " + month + " diarsipkan");
        } catch (IllegalArgumentException | EntityNotFoundException | IOException e) {
            redirectAttributes.addFlashAttribute(FLASH_ERROR, e.getMessage());
        }
        return REDIRECT + month.getYear();
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('" + Permission.EVIDENCE_MANAGE + "')")
    public String delete(@PathVariable UUID id, @RequestParam int year, RedirectAttributes redirectAttributes) {
        try {
            archiveService.delete(id, LocalDate.now());
            redirectAttributes.addFlashAttribute(FLASH_SUCCESS, "Arsip rekening koran dihapus");
        } catch (IllegalStateException | IOException e) {
            redirectAttributes.addFlashAttribute(FLASH_ERROR, e.getMessage());
        }
        return REDIRECT + year;
    }

    private int graceDays() {
        AlertRule rule = alertRuleRepository.findByAlertType(AlertType.BANK_STATEMENT_MISSING)
                .orElseThrow(() -> new IllegalStateException("Aturan peringatan BANK_STATEMENT_MISSING tidak ditemukan"));
        return rule.getThreshold().intValue();
    }
}
