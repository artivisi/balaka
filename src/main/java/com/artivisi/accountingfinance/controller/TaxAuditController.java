package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.enums.SelfTestLine;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.TaxAuditWorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

import static com.artivisi.accountingfinance.controller.ViewConstants.ATTR_CURRENT_PAGE;
import static com.artivisi.accountingfinance.controller.ViewConstants.PAGE_TAX_AUDIT;

/** Tax audit workspace (issue #51): year view, trace of one self-test row, drill-down from a ledger line. */
@Controller
@RequestMapping("/tax-audit")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permission.TAX_AUDIT_VIEW + "')")
public class TaxAuditController {

    private final TaxAuditWorkspaceService workspaceService;

    /** Without a year only the selection form shows. */
    @GetMapping
    public String year(@RequestParam(required = false) Integer year, Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_TAX_AUDIT);
        model.addAttribute("year", year);
        if (year != null) {
            model.addAttribute("view", workspaceService.year(year));
        }
        return "tax-audit/workspace";
    }

    @GetMapping("/trace")
    public String trace(@RequestParam int year, @RequestParam SelfTestLine line, @RequestParam int month, Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_TAX_AUDIT);
        model.addAttribute("year", year);
        model.addAttribute("line", line);
        model.addAttribute("month", month);
        try {
            model.addAttribute("view", workspaceService.trace(year, line, month));
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("errorMessage", e.getMessage());
        }
        return "tax-audit/trace";
    }

    @GetMapping("/transactions/{id}")
    public String ledger(@PathVariable UUID id, @RequestParam(required = false) UUID line, Model model) {
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_TAX_AUDIT);
        model.addAttribute("view", workspaceService.ledger(id, line));
        return "tax-audit/ledger";
    }
}
