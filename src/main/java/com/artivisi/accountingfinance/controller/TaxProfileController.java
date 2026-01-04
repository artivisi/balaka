package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.CompanyConfig;
import com.artivisi.accountingfinance.enums.AuditEventType;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.CompanyConfigService;
import com.artivisi.accountingfinance.service.SecurityAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;

/**
 * Controller for Tax Profile page.
 * Displays and manages company tax classification information.
 * This is informational only - does not affect recording or calculation.
 */
@Controller
@RequestMapping("/tax-profile")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAuthority('" + Permission.SETTINGS_VIEW + "')")
public class TaxProfileController {

    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";
    private static final String ATTR_ERROR_MESSAGE = "errorMessage";
    private static final String ATTR_CURRENT_PAGE = "currentPage";

    private final CompanyConfigService companyConfigService;
    private final SecurityAuditService securityAuditService;

    @GetMapping
    public String showTaxProfile(Model model) {
        CompanyConfig config = companyConfigService.getConfig();

        model.addAttribute("config", config);
        model.addAttribute(ATTR_CURRENT_PAGE, "tax-profile");

        // Calculate derived tax classification info
        if (config.getEstablishedDate() != null) {
            Period age = Period.between(config.getEstablishedDate(), LocalDate.now());
            int years = age.getYears();
            int months = age.getMonths();

            model.addAttribute("companyAgeYears", years);
            model.addAttribute("companyAgeMonths", months);
            model.addAttribute("companyAgeText", formatAge(years, months));

            // PPh regime eligibility based on company age
            boolean eligibleForPPhFinal = years < 4;
            model.addAttribute("eligibleForPPhFinal", eligibleForPPhFinal);

            if (eligibleForPPhFinal) {
                model.addAttribute("pphRegimeText", "Eligible untuk PPh Final UMKM 0,5% (berdasarkan usia perusahaan)");
            } else {
                model.addAttribute("pphRegimeText", "PPh Badan dengan fasilitas Pasal 31E (tarif efektif 11% untuk omzet s.d. 4,8M)");
            }
        }

        // PKP status info
        if (config.getIsPkp() != null && config.getIsPkp()) {
            model.addAttribute("ppnStatusText", "Wajib pungut PPN 11%");
            if (config.getPkpSince() != null) {
                Period pkpAge = Period.between(config.getPkpSince(), LocalDate.now());
                model.addAttribute("pkpDuration", formatAge(pkpAge.getYears(), pkpAge.getMonths()));
            }
        } else {
            model.addAttribute("ppnStatusText", "Belum PKP - tidak wajib pungut PPN");
        }

        return "tax-profile/index";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permission.SETTINGS_EDIT + "')")
    public String updateTaxProfile(
            @RequestParam(required = false) String establishedDate,
            @RequestParam(required = false) Boolean isPkp,
            @RequestParam(required = false) String pkpSince,
            RedirectAttributes redirectAttributes) {

        try {
            CompanyConfig config = companyConfigService.getConfig();

            // Parse and set established date
            if (establishedDate != null && !establishedDate.isBlank()) {
                config.setEstablishedDate(LocalDate.parse(establishedDate, DateTimeFormatter.ISO_DATE));
            } else {
                config.setEstablishedDate(null);
            }

            // Set PKP status
            config.setIsPkp(isPkp != null && isPkp);

            // Parse and set PKP since date (only if PKP is true)
            if (Boolean.TRUE.equals(isPkp) && pkpSince != null && !pkpSince.isBlank()) {
                config.setPkpSince(LocalDate.parse(pkpSince, DateTimeFormatter.ISO_DATE));
            } else {
                config.setPkpSince(null);
            }

            companyConfigService.save(config);
            securityAuditService.log(AuditEventType.SETTINGS_CHANGE,
                    "Tax profile updated: established=" + config.getEstablishedDate() +
                    ", isPkp=" + config.getIsPkp() +
                    ", pkpSince=" + config.getPkpSince());

            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Profil pajak berhasil disimpan");
        } catch (Exception e) {
            log.error("Failed to update tax profile", e);
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, "Gagal menyimpan profil pajak: " + e.getMessage());
        }

        return "redirect:/tax-profile";
    }

    private String formatAge(int years, int months) {
        StringBuilder sb = new StringBuilder();
        if (years > 0) {
            sb.append(years).append(" tahun");
        }
        if (months > 0) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(months).append(" bulan");
        }
        if (sb.isEmpty()) {
            return "< 1 bulan";
        }
        return sb.toString();
    }
}
