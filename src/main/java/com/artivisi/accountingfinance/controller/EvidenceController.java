package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.enums.EvidenceType;
import com.artivisi.accountingfinance.repository.ChartOfAccountRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.AuditPackService;
import com.artivisi.accountingfinance.service.CompanyConfigService;
import com.artivisi.accountingfinance.service.EvidencePostingGuard;
import com.artivisi.accountingfinance.service.EvidenceQueryService;
import com.artivisi.accountingfinance.service.EvidenceRequirementService;
import com.artivisi.accountingfinance.service.SecurityAuditService;
import com.artivisi.accountingfinance.enums.AuditEventType;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import static com.artivisi.accountingfinance.controller.ViewConstants.ATTR_CURRENT_PAGE;
import static com.artivisi.accountingfinance.controller.ViewConstants.PAGE_EVIDENCE;

/** Evidence requirement rules, missing-evidence report, per-transaction panel and the yearly audit pack. */
@Controller
@RequestMapping("/evidence")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permission.EVIDENCE_VIEW + "')")
public class EvidenceController {

    private static final String FLASH_SUCCESS = "successMessage";
    private static final String FLASH_ERROR = "errorMessage";
    private static final String REDIRECT_REQUIREMENTS = "redirect:/evidence/requirements";

    private final EvidenceQueryService evidenceQueryService;
    private final EvidenceRequirementService requirementService;
    private final AuditPackService auditPackService;
    private final ChartOfAccountRepository accountRepository;
    private final JournalTemplateRepository templateRepository;
    private final SecurityAuditService securityAuditService;
    private final EvidencePostingGuard evidencePostingGuard;
    private final CompanyConfigService companyConfigService;

    @GetMapping("/requirements")
    public String requirements(Model model) {
        model.addAttribute("requirements", evidenceQueryService.requirements());
        model.addAttribute("accounts", accountRepository.findByActiveOrderByAccountCodeAsc(true));
        model.addAttribute("templates", templateRepository.findByActiveAndIsCurrentVersionTrueOrderByTemplateNameAsc(true));
        model.addAttribute("evidenceTypes", EvidenceType.requirableValues());
        model.addAttribute("evidenceRequiredFrom", evidencePostingGuard.requiredFrom());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_EVIDENCE);
        return "evidence/requirements";
    }

    /** Evidence at posting: blank date turns the check off. */
    @PostMapping("/requirements/posting-policy")
    @PreAuthorize("hasAuthority('" + Permission.EVIDENCE_CONFIG + "')")
    public String savePostingPolicy(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate requiredFrom,
                                    RedirectAttributes redirectAttributes) {
        companyConfigService.setEvidenceRequiredFrom(requiredFrom);
        redirectAttributes.addFlashAttribute(FLASH_SUCCESS, requiredFrom == null
                ? "Pemeriksaan bukti saat posting dimatikan"
                : "Posting transaksi bertanggal " + requiredFrom + " ke atas wajib berbukti lengkap");
        return REDIRECT_REQUIREMENTS;
    }

    @PostMapping("/requirements")
    @PreAuthorize("hasAuthority('" + Permission.EVIDENCE_CONFIG + "')")
    public String createRequirement(@RequestParam(required = false) UUID accountId,
                                    @RequestParam(required = false) UUID templateId,
                                    @RequestParam(required = false) EvidenceType evidenceType,
                                    @RequestParam(required = false) BigDecimal minAmount,
                                    @RequestParam(required = false) String notes,
                                    Authentication authentication,
                                    RedirectAttributes redirectAttributes) {
        try {
            requirementService.create(new EvidenceRequirementService.NewRequirement(accountId, templateId, evidenceType,
                    minAmount, notes, authentication.getName()));
            redirectAttributes.addFlashAttribute(FLASH_SUCCESS, "Aturan bukti tersimpan");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute(FLASH_ERROR, e.getMessage());
        }
        return REDIRECT_REQUIREMENTS;
    }

    @PostMapping("/requirements/{id}/delete")
    @PreAuthorize("hasAuthority('" + Permission.EVIDENCE_CONFIG + "')")
    public String deleteRequirement(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        requirementService.delete(id);
        redirectAttributes.addFlashAttribute(FLASH_SUCCESS, "Aturan bukti dihapus");
        return REDIRECT_REQUIREMENTS;
    }

    @GetMapping("/missing")
    public String missing(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          Model model) {
        // The form opens on the current month; an explicit period is always shown as entered.
        YearMonth current = YearMonth.now();
        LocalDate start = from != null ? from : current.atDay(1);
        LocalDate end = to != null ? to : current.atEndOfMonth();
        model.addAttribute("from", start);
        model.addAttribute("to", end);
        try {
            var index = evidenceQueryService.index(start, end);
            model.addAttribute("gaps", index.stream().filter(e -> !e.complete()).toList());
            model.addAttribute("checkedCount", index.size());
            model.addAttribute("requiredCount", index.stream().filter(e -> !e.required().isEmpty()).count());
        } catch (IllegalArgumentException e) {
            model.addAttribute(FLASH_ERROR, e.getMessage());
            model.addAttribute("gaps", java.util.List.of());
            model.addAttribute("checkedCount", 0);
            model.addAttribute("requiredCount", 0L);
        }
        model.addAttribute("ruleCount", evidenceQueryService.requirements().size());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_EVIDENCE);
        return "evidence/missing";
    }

    /** Evidence panel on the transaction detail page (HTMX). */
    @GetMapping("/transactions/{id}")
    public String transactionPanel(@PathVariable UUID id, Model model) {
        EvidenceQueryService.TransactionEvidence evidence = evidenceQueryService.evidenceFor(id);
        model.addAttribute("evidence", evidence);
        LocalDate requiredFrom = evidencePostingGuard.requiredFrom();
        model.addAttribute("postingRequiresEvidence", requiredFrom != null && evidence.transactionNumber() == null
                && !evidence.transactionDate().isBefore(requiredFrom) ? requiredFrom : null);
        return "evidence/fragments/transaction-panel :: panel";
    }

    @GetMapping("/audit-pack")
    @PreAuthorize("hasAuthority('" + Permission.REPORT_EXPORT + "')")
    public String auditPackPage(@RequestParam(required = false) Integer year, Model model) {
        model.addAttribute("year", year != null ? year : LocalDate.now().getYear() - 1);
        model.addAttribute("maxDocumentBytes", auditPackService.maxDocumentBytes());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_EVIDENCE);
        return "evidence/audit-pack";
    }

    @GetMapping("/audit-pack/download")
    @PreAuthorize("hasAuthority('" + Permission.REPORT_EXPORT + "')")
    public String downloadAuditPack(@RequestParam int year,
                                    @RequestParam(defaultValue = "false") boolean includeFiles,
                                    Authentication authentication,
                                    HttpServletResponse response,
                                    RedirectAttributes redirectAttributes) throws IOException {
        AuditPackService.AuditPackPlan plan;
        try {
            plan = auditPackService.plan(year, includeFiles);
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute(FLASH_ERROR, e.getMessage());
            return "redirect:/evidence/audit-pack?year=" + year;
        }
        securityAuditService.log(AuditEventType.DATA_EXPORT, "Audit pack " + year + (includeFiles ? " with files" : ""));
        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=paket-audit-" + year + ".zip");
        auditPackService.write(plan, authentication.getName(), response.getOutputStream());
        response.flushBuffer();
        return null;
    }
}
