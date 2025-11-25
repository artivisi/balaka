package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.Project;
import com.artivisi.accountingfinance.entity.ProjectPaymentTerm;
import com.artivisi.accountingfinance.enums.PaymentTrigger;
import com.artivisi.accountingfinance.service.InvoiceService;
import com.artivisi.accountingfinance.service.ProjectMilestoneService;
import com.artivisi.accountingfinance.service.ProjectPaymentTermService;
import com.artivisi.accountingfinance.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/projects/{projectId}/payment-terms")
@RequiredArgsConstructor
public class PaymentTermController {

    private final ProjectPaymentTermService paymentTermService;
    private final ProjectService projectService;
    private final ProjectMilestoneService milestoneService;
    private final InvoiceService invoiceService;

    @GetMapping("/new")
    public String newForm(@PathVariable UUID projectId, Model model) {
        Project project = projectService.findById(projectId);
        ProjectPaymentTerm paymentTerm = new ProjectPaymentTerm();

        model.addAttribute("project", project);
        model.addAttribute("paymentTerm", paymentTerm);
        model.addAttribute("milestones", milestoneService.findByProjectId(projectId));
        model.addAttribute("triggers", PaymentTrigger.values());
        model.addAttribute("currentPage", "projects");
        return "payment-terms/form";
    }

    @PostMapping("/new")
    public String create(
            @PathVariable UUID projectId,
            @Valid @ModelAttribute("paymentTerm") ProjectPaymentTerm paymentTerm,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            Project project = projectService.findById(projectId);
            model.addAttribute("project", project);
            model.addAttribute("milestones", milestoneService.findByProjectId(projectId));
            model.addAttribute("triggers", PaymentTrigger.values());
            model.addAttribute("currentPage", "projects");
            return "payment-terms/form";
        }

        try {
            paymentTermService.create(projectId, paymentTerm);
            redirectAttributes.addFlashAttribute("successMessage", "Termin pembayaran berhasil ditambahkan");
            return "redirect:/projects/" + projectId;
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("sequence", "duplicate", e.getMessage());
            Project project = projectService.findById(projectId);
            model.addAttribute("project", project);
            model.addAttribute("milestones", milestoneService.findByProjectId(projectId));
            model.addAttribute("triggers", PaymentTrigger.values());
            model.addAttribute("currentPage", "projects");
            return "payment-terms/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(
            @PathVariable UUID projectId,
            @PathVariable UUID id,
            Model model) {

        Project project = projectService.findById(projectId);
        ProjectPaymentTerm paymentTerm = paymentTermService.findById(id);

        model.addAttribute("project", project);
        model.addAttribute("paymentTerm", paymentTerm);
        model.addAttribute("milestones", milestoneService.findByProjectId(projectId));
        model.addAttribute("triggers", PaymentTrigger.values());
        model.addAttribute("currentPage", "projects");
        return "payment-terms/form";
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable UUID projectId,
            @PathVariable UUID id,
            @Valid @ModelAttribute("paymentTerm") ProjectPaymentTerm paymentTerm,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            Project project = projectService.findById(projectId);
            paymentTerm.setId(id);
            model.addAttribute("project", project);
            model.addAttribute("milestones", milestoneService.findByProjectId(projectId));
            model.addAttribute("triggers", PaymentTrigger.values());
            model.addAttribute("currentPage", "projects");
            return "payment-terms/form";
        }

        try {
            paymentTermService.update(id, paymentTerm);
            redirectAttributes.addFlashAttribute("successMessage", "Termin pembayaran berhasil diperbarui");
            return "redirect:/projects/" + projectId;
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("sequence", "duplicate", e.getMessage());
            Project project = projectService.findById(projectId);
            paymentTerm.setId(id);
            model.addAttribute("project", project);
            model.addAttribute("milestones", milestoneService.findByProjectId(projectId));
            model.addAttribute("triggers", PaymentTrigger.values());
            model.addAttribute("currentPage", "projects");
            return "payment-terms/form";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(
            @PathVariable UUID projectId,
            @PathVariable UUID id,
            RedirectAttributes redirectAttributes) {

        paymentTermService.delete(id);
        redirectAttributes.addFlashAttribute("successMessage", "Termin pembayaran berhasil dihapus");
        return "redirect:/projects/" + projectId;
    }

    @PostMapping("/{id}/generate-invoice")
    public String generateInvoice(
            @PathVariable UUID projectId,
            @PathVariable UUID id,
            RedirectAttributes redirectAttributes) {

        try {
            invoiceService.createFromPaymentTerm(id);
            redirectAttributes.addFlashAttribute("successMessage", "Invoice berhasil dibuat dari termin pembayaran");
            return "redirect:/projects/" + projectId;
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/projects/" + projectId;
        }
    }
}
