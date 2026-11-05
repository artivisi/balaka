package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.enums.SalesTaxExceptionType;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.SalesTaxChainService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.YearMonth;

import static com.artivisi.accountingfinance.controller.ViewConstants.ATTR_CURRENT_PAGE;
import static com.artivisi.accountingfinance.controller.ViewConstants.PAGE_SALES_TAX_CHAIN;

/** Monthly reconciliation and exception report of the chain from invoice to faktur, receipt and bupot. */
@Controller
@RequestMapping("/sales-tax-chain")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permission.TAX_REPORT_VIEW + "')")
public class SalesTaxChainController {

    private final SalesTaxChainService chainService;

    /** Exceptions do not expire with their masa, so the list runs up to the end of the selected masa. */
    @GetMapping
    public String report(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth masa, Model model) {
        YearMonth period = masa != null ? masa : YearMonth.now();
        model.addAttribute("masa", period);
        model.addAttribute("recon", chainService.getReconciliation(period.getYear(), period.getMonthValue()));
        model.addAttribute("exceptions", chainService.findExceptions(null, period.atEndOfMonth()));
        model.addAttribute("exceptionTypes", SalesTaxExceptionType.values());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_SALES_TAX_CHAIN);
        return "sales-tax-chain/report";
    }
}
