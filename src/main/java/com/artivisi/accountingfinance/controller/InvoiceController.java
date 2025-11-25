package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.Invoice;
import com.artivisi.accountingfinance.enums.InvoiceStatus;
import com.artivisi.accountingfinance.service.ClientService;
import com.artivisi.accountingfinance.service.InvoiceService;
import com.artivisi.accountingfinance.service.ProjectService;
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

@Controller
@RequestMapping("/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final ClientService clientService;
    private final ProjectService projectService;

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
        model.addAttribute("clients", clientService.findActiveClients());
        model.addAttribute("projects", projectService.findActiveProjects());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedClientId", clientId);
        model.addAttribute("selectedProjectId", projectId);
        model.addAttribute("currentPage", "invoices");

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

        model.addAttribute("invoice", invoice);
        model.addAttribute("clients", clientService.findActiveClients());
        model.addAttribute("projects", projectService.findActiveProjects());
        model.addAttribute("currentPage", "invoices");
        return "invoices/form";
    }

    @PostMapping("/new")
    public String create(
            @Valid @ModelAttribute("invoice") Invoice invoice,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("clients", clientService.findActiveClients());
            model.addAttribute("projects", projectService.findActiveProjects());
            model.addAttribute("currentPage", "invoices");
            return "invoices/form";
        }

        try {
            Invoice saved = invoiceService.create(invoice);
            redirectAttributes.addFlashAttribute("successMessage", "Invoice berhasil dibuat");
            return "redirect:/invoices/" + saved.getId();
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("invoiceNumber", "duplicate", e.getMessage());
            model.addAttribute("clients", clientService.findActiveClients());
            model.addAttribute("projects", projectService.findActiveProjects());
            model.addAttribute("currentPage", "invoices");
            return "invoices/form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        Invoice invoice = invoiceService.findById(id);

        model.addAttribute("invoice", invoice);
        model.addAttribute("currentPage", "invoices");
        return "invoices/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model) {
        Invoice invoice = invoiceService.findById(id);

        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            return "redirect:/invoices/" + id;
        }

        model.addAttribute("invoice", invoice);
        model.addAttribute("clients", clientService.findActiveClients());
        model.addAttribute("projects", projectService.findActiveProjects());
        model.addAttribute("currentPage", "invoices");
        return "invoices/form";
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable UUID id,
            @Valid @ModelAttribute("invoice") Invoice invoice,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            invoice.setId(id);
            model.addAttribute("clients", clientService.findActiveClients());
            model.addAttribute("projects", projectService.findActiveProjects());
            model.addAttribute("currentPage", "invoices");
            return "invoices/form";
        }

        try {
            invoiceService.update(id, invoice);
            redirectAttributes.addFlashAttribute("successMessage", "Invoice berhasil diperbarui");
            return "redirect:/invoices/" + id;
        } catch (IllegalArgumentException | IllegalStateException e) {
            if (e.getMessage().contains("already exists")) {
                bindingResult.rejectValue("invoiceNumber", "duplicate", e.getMessage());
            } else {
                bindingResult.reject("error", e.getMessage());
            }
            invoice.setId(id);
            model.addAttribute("clients", clientService.findActiveClients());
            model.addAttribute("projects", projectService.findActiveProjects());
            model.addAttribute("currentPage", "invoices");
            return "invoices/form";
        }
    }

    @PostMapping("/{id}/send")
    public String send(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            invoiceService.send(id);
            redirectAttributes.addFlashAttribute("successMessage", "Invoice berhasil dikirim");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/invoices/" + id;
    }

    @PostMapping("/{id}/mark-paid")
    public String markPaid(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            invoiceService.markAsPaid(id);
            redirectAttributes.addFlashAttribute("successMessage", "Invoice ditandai sudah dibayar");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/invoices/" + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            invoiceService.cancel(id);
            redirectAttributes.addFlashAttribute("successMessage", "Invoice dibatalkan");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/invoices/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            invoiceService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "Invoice berhasil dihapus");
            return "redirect:/invoices";
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/invoices/" + id;
        }
    }
}
