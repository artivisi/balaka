package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.dto.AccountStatement;
import com.artivisi.accountingfinance.entity.Client;
import com.artivisi.accountingfinance.entity.Vendor;
import com.artivisi.accountingfinance.service.ClientService;
import com.artivisi.accountingfinance.service.StatementService;
import com.artivisi.accountingfinance.service.VendorService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

import static com.artivisi.accountingfinance.controller.ViewConstants.*;
import static com.artivisi.accountingfinance.security.Permission.REPORT_VIEW;

@Controller
@RequestMapping("/statements")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + REPORT_VIEW + "')")
public class StatementController {

    private static final String ATTR_STATEMENT = "statement";

    private final StatementService statementService;
    private final ClientService clientService;
    private final VendorService vendorService;

    @GetMapping("/client/{code}")
    public String clientStatement(
            @PathVariable String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            Model model) {

        Client client = clientService.findByCode(code);

        if (dateFrom == null) {
            dateFrom = LocalDate.now().withDayOfMonth(1);
        }
        if (dateTo == null) {
            dateTo = LocalDate.now();
        }

        AccountStatement statement = statementService.generateClientStatement(
                client.getId(), client.getCode(), client.getName(), dateFrom, dateTo);

        model.addAttribute(ATTR_STATEMENT, statement);
        model.addAttribute("client", client);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_CLIENT_STATEMENT);
        return "statements/client";
    }

    @GetMapping("/client/{code}/print")
    public String clientStatementPrint(
            @PathVariable String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            Model model) {

        Client client = clientService.findByCode(code);

        if (dateFrom == null) {
            dateFrom = LocalDate.now().withDayOfMonth(1);
        }
        if (dateTo == null) {
            dateTo = LocalDate.now();
        }

        AccountStatement statement = statementService.generateClientStatement(
                client.getId(), client.getCode(), client.getName(), dateFrom, dateTo);

        model.addAttribute(ATTR_STATEMENT, statement);
        model.addAttribute("entityName", client.getName());
        model.addAttribute("entityCode", client.getCode());
        model.addAttribute("reportTitle", "Laporan Piutang");
        model.addAttribute("documentType", "INVOICE");
        model.addAttribute("documentLabel", "Invoice");
        model.addAttribute("columnLabel", "Invoice");
        return "statements/statement-print";
    }

    @GetMapping("/vendor/{code}")
    public String vendorStatement(
            @PathVariable String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            Model model) {

        Vendor vendor = vendorService.findByCode(code);

        if (dateFrom == null) {
            dateFrom = LocalDate.now().withDayOfMonth(1);
        }
        if (dateTo == null) {
            dateTo = LocalDate.now();
        }

        AccountStatement statement = statementService.generateVendorStatement(
                vendor.getId(), vendor.getCode(), vendor.getName(), dateFrom, dateTo);

        model.addAttribute(ATTR_STATEMENT, statement);
        model.addAttribute("vendor", vendor);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_VENDOR_STATEMENT);
        return "statements/vendor";
    }

    @GetMapping("/vendor/{code}/print")
    public String vendorStatementPrint(
            @PathVariable String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            Model model) {

        Vendor vendor = vendorService.findByCode(code);

        if (dateFrom == null) {
            dateFrom = LocalDate.now().withDayOfMonth(1);
        }
        if (dateTo == null) {
            dateTo = LocalDate.now();
        }

        AccountStatement statement = statementService.generateVendorStatement(
                vendor.getId(), vendor.getCode(), vendor.getName(), dateFrom, dateTo);

        model.addAttribute(ATTR_STATEMENT, statement);
        model.addAttribute("entityName", vendor.getName());
        model.addAttribute("entityCode", vendor.getCode());
        model.addAttribute("reportTitle", "Laporan Hutang");
        model.addAttribute("documentType", "BILL");
        model.addAttribute("documentLabel", "Tagihan");
        model.addAttribute("columnLabel", "Tagihan");
        return "statements/statement-print";
    }
}
