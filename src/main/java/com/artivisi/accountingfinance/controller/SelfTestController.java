package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.enums.SelfTestLine;
import com.artivisi.accountingfinance.service.SelfTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static com.artivisi.accountingfinance.controller.ViewConstants.ATTR_CURRENT_PAGE;
import static com.artivisi.accountingfinance.controller.ViewConstants.PAGE_SELF_TEST;

/** Tax-office self-test (issue #45). Without a year only the selection form shows. */
@Controller
@RequestMapping("/reports/self-test")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('TAX_REPORT_VIEW')")
public class SelfTestController {

    private static final String VIEW = "reports/self-test";

    private final SelfTestService selfTestService;

    @GetMapping
    public String report(@RequestParam(required = false) Integer year, Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_SELF_TEST);
        model.addAttribute("year", year);
        model.addAttribute("threshold", selfTestService.unitemisedThreshold());
        if (year != null) {
            model.addAttribute("report", selfTestService.report(year));
        }
        return VIEW;
    }

    @PostMapping("/explanation")
    @PreAuthorize("hasAuthority('FISCAL_PERIOD_MANAGE')")
    public String saveExplanation(@RequestParam int year, @RequestParam SelfTestLine line, @RequestParam int month,
                                  @RequestParam(required = false) String explanation, Authentication authentication,
                                  RedirectAttributes redirectAttributes) {
        try {
            boolean removed = selfTestService.saveExplanation(year, line, month, explanation, authentication.getName()) == null;
            redirectAttributes.addFlashAttribute("successMessage", removed
                    ? "Penjelasan " + line.getTitle() + " dihapus"
                    : "Penjelasan " + line.getTitle() + " disimpan");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/reports/self-test?year=" + year + "#line-" + line.name() + "-" + month;
    }
}
