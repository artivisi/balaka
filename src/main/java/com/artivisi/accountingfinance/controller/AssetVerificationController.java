package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.AssetCondition;
import com.artivisi.accountingfinance.entity.AssetVerificationResult;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.AssetVerificationService;
import com.artivisi.accountingfinance.service.AssetVerificationService.ChecklistRow;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.artivisi.accountingfinance.controller.ViewConstants.ATTR_CURRENT_PAGE;
import static com.artivisi.accountingfinance.controller.ViewConstants.PAGE_ASSETS;

@Controller
@RequestMapping("/assets/verification")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permission.ASSET_VIEW + "')")
public class AssetVerificationController {

    private final AssetVerificationService verificationService;

    @GetMapping
    public String checklist(@RequestParam(required = false) Integer year, Model model) {
        int selected = year != null ? year : LocalDate.now().getYear();
        List<ChecklistRow> rows = verificationService.checklist(selected);
        model.addAttribute("year", selected);
        model.addAttribute("rows", rows);
        model.addAttribute("exceptions", rows.stream().filter(ChecklistRow::hasExceptions).toList());
        model.addAttribute("verifiedCount", rows.stream().filter(r -> r.verification() != null).count());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("conditions", AssetCondition.values());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_ASSETS);
        return "assets/verification";
    }

    @PostMapping("/{assetId}")
    @PreAuthorize("hasAuthority('" + Permission.ASSET_EDIT + "')")
    public String record(@PathVariable UUID assetId,
                         @RequestParam int year,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate verifiedDate,
                         @RequestParam AssetVerificationResult result,
                         @RequestParam(required = false) AssetCondition condition,
                         @RequestParam(required = false) String location,
                         @RequestParam(required = false) String custodian,
                         @RequestParam(required = false) String notes,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        try {
            verificationService.record(new AssetVerificationService.RecordVerification(assetId, year, verifiedDate, result,
                    condition, location, custodian, notes, authentication.getName()));
            redirectAttributes.addFlashAttribute("successMessage", "Hasil verifikasi tersimpan");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/assets/verification?year=" + year;
    }
}
