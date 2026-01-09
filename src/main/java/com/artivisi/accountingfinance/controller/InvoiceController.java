package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.CompanyBankAccount;
import com.artivisi.accountingfinance.entity.CompanyConfig;
import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.enums.InvoiceStatus;
import com.artivisi.accountingfinance.service.ClientService;
import com.artivisi.accountingfinance.service.CompanyBankAccountService;
import com.artivisi.accountingfinance.service.CompanyConfigService;
import com.artivisi.accountingfinance.service.InvoiceService;
import com.artivisi.accountingfinance.service.ProjectService;
import com.artivisi.accountingfinance.util.AmountToWordsUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

import static com.artivisi.accountingfinance.controller.ViewConstants.*;

@Controller
@RequestMapping("/invoices")
@RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasAuthority('" + com.artivisi.accountingfinance.security.Permission.INVOICE_VIEW + "')")
public class InvoiceController {

    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";
    private static final String ATTR_ERROR_MESSAGE = "errorMessage";
    private static final String ATTR_INVOICE = "invoice";
    private static final String ATTR_CLIENTS = "clients";
    private static final String ATTR_PROJECTS = "projects";
    private static final String REDIRECT_INVOICES_PREFIX = "redirect:/invoices/";
    private static final String VIEW_FORM = "invoices/form";

    private final InvoiceService invoiceService;
    private final ClientService clientService;
    private final ProjectService projectService;
    private final CompanyConfigService companyConfigService;
    private final CompanyBankAccountService bankAccountService;

    @GetMapping
    public String list(
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) UUID projectId,
            @PageableDefault(size = 20) Pageable pageable,
            Model model) {

        Page<Invoice> invoices = invoiceService.findByFilters(status, clientId, projectId, pageable);

        model.addAttribute("invoices", invoices);
        model.addAttribute("statuses", InvoiceStatus.values());
        model.addAttribute(ATTR_CLIENTS, clientService.findActiveClients());
        model.addAttribute(ATTR_PROJECTS, projectService.findActiveProjects());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedClientId", clientId);
        model.addAttribute("selectedProjectId", projectId);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_INVOICES);

        // Summary counts
        model.addAttribute("draftCount", invoiceService.countByStatus(InvoiceStatus.DRAFT));
        model.addAttribute("sentCount", invoiceService.countByStatus(InvoiceStatus.SENT));
        model.addAttribute("overdueCount", invoiceService.countByStatus(InvoiceStatus.OVERDUE));
        model.addAttribute("paidCount", invoiceService.countByStatus(InvoiceStatus.PAID));

        return "invoices/list";
    }

    @GetMapping("/new")
    public String newForm(
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) UUID projectId,
            Model model) {

        Invoice invoice = new Invoice();

        // Pre-select client/project if provided
        if (clientId != null) {
            invoice.setClient(clientService.findById(clientId));
        }
        if (projectId != null) {
            invoice.setProject(projectService.findById(projectId));
        }

        model.addAttribute(ATTR_INVOICE, invoice);
        model.addAttribute(ATTR_CLIENTS, clientService.findActiveClients());
        model.addAttribute(ATTR_PROJECTS, projectService.findActiveProjects());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_INVOICES);
        return VIEW_FORM;
    }

    @PostMapping("/new")
    public String create(
            @Valid @ModelAttribute(ATTR_INVOICE) Invoice invoice,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute(ATTR_CLIENTS, clientService.findActiveClients());
            model.addAttribute(ATTR_PROJECTS, projectService.findActiveProjects());
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_INVOICES);
            return VIEW_FORM;
        }

        try {
            Invoice saved = invoiceService.create(invoice);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Invoice berhasil dibuat");
            return REDIRECT_INVOICES_PREFIX + saved.getInvoiceNumber();
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("invoiceNumber", "duplicate", e.getMessage());
            model.addAttribute(ATTR_CLIENTS, clientService.findActiveClients());
            model.addAttribute(ATTR_PROJECTS, projectService.findActiveProjects());
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_INVOICES);
            return VIEW_FORM;
        }
    }

    @GetMapping("/{invoiceNumber}")
    public String detail(@PathVariable String invoiceNumber, Model model) {
        Invoice invoice = invoiceService.findByInvoiceNumber(invoiceNumber);

        model.addAttribute(ATTR_INVOICE, invoice);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_INVOICES);
        return "invoices/detail";
    }

    @GetMapping("/{invoiceNumber}/edit")
    public String editForm(@PathVariable String invoiceNumber, Model model) {
        Invoice invoice = invoiceService.findByInvoiceNumber(invoiceNumber);

        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            return REDIRECT_INVOICES_PREFIX + invoiceNumber;
        }

        model.addAttribute(ATTR_INVOICE, invoice);
        model.addAttribute(ATTR_CLIENTS, clientService.findActiveClients());
        model.addAttribute(ATTR_PROJECTS, projectService.findActiveProjects());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_INVOICES);
        return VIEW_FORM;
    }

    @PostMapping("/{invoiceNumber}")
    public String update(
            @PathVariable String invoiceNumber,
            @Valid @ModelAttribute(ATTR_INVOICE) Invoice invoice,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            Invoice existing = invoiceService.findByInvoiceNumber(invoiceNumber);
            invoice.setId(existing.getId());
            model.addAttribute(ATTR_CLIENTS, clientService.findActiveClients());
            model.addAttribute(ATTR_PROJECTS, projectService.findActiveProjects());
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_INVOICES);
            return VIEW_FORM;
        }

        try {
            Invoice existing = invoiceService.findByInvoiceNumber(invoiceNumber);
            invoiceService.update(existing.getId(), invoice);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Invoice berhasil diperbarui");
            return REDIRECT_INVOICES_PREFIX + invoice.getInvoiceNumber();
        } catch (IllegalArgumentException | IllegalStateException e) {
            if (e.getMessage().contains("already exists")) {
                bindingResult.rejectValue("invoiceNumber", "duplicate", e.getMessage());
            } else {
                bindingResult.reject("error", e.getMessage());
            }
            Invoice existing = invoiceService.findByInvoiceNumber(invoiceNumber);
            invoice.setId(existing.getId());
            model.addAttribute(ATTR_CLIENTS, clientService.findActiveClients());
            model.addAttribute(ATTR_PROJECTS, projectService.findActiveProjects());
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_INVOICES);
            return VIEW_FORM;
        }
    }

    @PostMapping("/{invoiceNumber}/send")
    public String send(@PathVariable String invoiceNumber, RedirectAttributes redirectAttributes) {
        try {
            Invoice invoice = invoiceService.findByInvoiceNumber(invoiceNumber);
            invoiceService.send(invoice.getId());
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Invoice berhasil dikirim");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
        }
        return REDIRECT_INVOICES_PREFIX + invoiceNumber;
    }

    @GetMapping("/{invoiceNumber}/pay")
    public String payForm(@PathVariable String invoiceNumber, RedirectAttributes redirectAttributes) {
        Invoice invoice = invoiceService.findByInvoiceNumber(invoiceNumber);

        if (invoice.getStatus() != InvoiceStatus.SENT && invoice.getStatus() != InvoiceStatus.OVERDUE) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, "Hanya invoice terkirim atau jatuh tempo yang dapat dibayar");
            return REDIRECT_INVOICES_PREFIX + invoiceNumber;
        }

        // Redirect to transaction form with invoice id and receipt template
        // Template: Terima Pelunasan Piutang (e0000000-0000-0000-0000-000000000010)
        return "redirect:/transactions/new?invoiceId=" + invoice.getId() + "&templateId=e0000000-0000-0000-0000-000000000010";
    }

    @PostMapping("/{invoiceNumber}/mark-paid")
    public String markPaid(@PathVariable String invoiceNumber, RedirectAttributes redirectAttributes) {
        try {
            Invoice invoice = invoiceService.findByInvoiceNumber(invoiceNumber);
            invoiceService.markAsPaid(invoice.getId());
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Invoice ditandai sudah dibayar");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
        }
        return REDIRECT_INVOICES_PREFIX + invoiceNumber;
    }

    @PostMapping("/{invoiceNumber}/cancel")
    public String cancel(@PathVariable String invoiceNumber, RedirectAttributes redirectAttributes) {
        try {
            Invoice invoice = invoiceService.findByInvoiceNumber(invoiceNumber);
            invoiceService.cancel(invoice.getId());
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Invoice dibatalkan");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
        }
        return REDIRECT_INVOICES_PREFIX + invoiceNumber;
    }

    @PostMapping("/{invoiceNumber}/delete")
    public String delete(@PathVariable String invoiceNumber, RedirectAttributes redirectAttributes) {
        try {
            Invoice invoice = invoiceService.findByInvoiceNumber(invoiceNumber);
            invoiceService.delete(invoice.getId());
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Invoice berhasil dihapus");
            return "redirect:/invoices";
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
            return REDIRECT_INVOICES_PREFIX + invoiceNumber;
        }
    }

    @GetMapping("/{invoiceNumber}/print")
    public String print(@PathVariable String invoiceNumber, Model model) {
        Invoice invoice = invoiceService.findByInvoiceNumber(invoiceNumber);
        CompanyConfig company = companyConfigService.getConfig();
        CompanyBankAccount bankAccount = bankAccountService.findDefaultAccount().orElse(null);

        model.addAttribute(ATTR_INVOICE, invoice);
        model.addAttribute("company", company);
        model.addAttribute("bankAccount", bankAccount);
        model.addAttribute("amountInWords", AmountToWordsUtil.toWords(invoice.getAmount()));

        return "invoices/print";
    }
}
