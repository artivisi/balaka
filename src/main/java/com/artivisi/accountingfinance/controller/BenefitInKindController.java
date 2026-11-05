package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.BenefitInKindService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Benefit-in-kind recipients on the transaction detail page. The value feeds an employee's PPh 21, so
 * writes require the payroll permission rather than a transaction permission.
 */
@Controller
@RequestMapping("/transactions/{transactionId}/benefits")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permission.PAYROLL_CREATE + "')")
public class BenefitInKindController {

    private static final String ATTR_SUCCESS = "successMessage";
    private static final String ATTR_ERROR = "benefitError";

    private final BenefitInKindService benefitInKindService;

    @PostMapping
    public String add(@PathVariable UUID transactionId,
                      @RequestParam UUID employeeId,
                      @RequestParam BigDecimal amount,
                      @RequestParam String description,
                      RedirectAttributes redirectAttributes) {
        try {
            benefitInKindService.add(transactionId, employeeId, amount, description);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS, "Penerima natura dicatat");
        } catch (IllegalArgumentException | IllegalStateException | jakarta.validation.ConstraintViolationException e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR, e.getMessage());
        }
        return redirect(transactionId);
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID transactionId,
                         @PathVariable UUID id,
                         RedirectAttributes redirectAttributes) {
        try {
            benefitInKindService.delete(id);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS, "Penerima natura dihapus");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR, e.getMessage());
        }
        return redirect(transactionId);
    }

    private String redirect(UUID transactionId) {
        return "redirect:/transactions/" + transactionId;
    }
}
