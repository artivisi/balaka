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

import static com.artivisi.accountingfinance.controller.ViewConstants.*;

@Controller
@RequestMapping("/projects/{projectCode}/payment-terms")
@RequiredArgsConstructor
public class PaymentTermController {

    private static final String ATTR_PROJECT = "project";
    private static final String ATTR_PAYMENT_TERM = "paymentTerm";
    private static final String ATTR_MILESTONES = "milestones";
    private static final String ATTR_TRIGGERS = "triggers";
    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";
    private static final String REDIRECT_PROJECT_PREFIX = "redirect:/projects/";
    private static final String VIEW_FORM = "payment-terms/form";

    private final ProjectPaymentTermService paymentTermService;
    private final ProjectService projectService;
    private final ProjectMilestoneService milestoneService;
    private final InvoiceService invoiceService;

    @GetMapping("/new")
    public String newForm(@PathVariable String projectCode, Model model) {
        Project project = projectService.findByCode(projectCode);
        ProjectPaymentTerm paymentTerm = new ProjectPaymentTerm();

        model.addAttribute(ATTR_PROJECT, project);
        model.addAttribute(ATTR_PAYMENT_TERM, paymentTerm);
        model.addAttribute(ATTR_MILESTONES, milestoneService.findByProjectId(project.getId()));
        model.addAttribute(ATTR_TRIGGERS, PaymentTrigger.values());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PROJECTS);
        return VIEW_FORM;
    }

    @PostMapping("/new")
    public String create(
            @PathVariable String projectCode,
            @Valid @ModelAttribute(ATTR_PAYMENT_TERM) ProjectPaymentTerm paymentTerm,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            Project project = projectService.findByCode(projectCode);
            model.addAttribute(ATTR_PROJECT, project);
            model.addAttribute(ATTR_MILESTONES, milestoneService.findByProjectId(project.getId()));
            model.addAttribute(ATTR_TRIGGERS, PaymentTrigger.values());
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PROJECTS);
            return VIEW_FORM;
        }

        try {
            Project project = projectService.findByCode(projectCode);
            paymentTermService.create(project.getId(), paymentTerm);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Termin pembayaran berhasil ditambahkan");
            return REDIRECT_PROJECT_PREFIX + projectCode;
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("sequence", "duplicate", e.getMessage());
            Project project = projectService.findByCode(projectCode);
            model.addAttribute(ATTR_PROJECT, project);
            model.addAttribute(ATTR_MILESTONES, milestoneService.findByProjectId(project.getId()));
            model.addAttribute(ATTR_TRIGGERS, PaymentTrigger.values());
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PROJECTS);
            return VIEW_FORM;
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(
            @PathVariable String projectCode,
            @PathVariable UUID id,
            Model model) {

        Project project = projectService.findByCode(projectCode);
        ProjectPaymentTerm paymentTerm = paymentTermService.findById(id);

        model.addAttribute(ATTR_PROJECT, project);
        model.addAttribute(ATTR_PAYMENT_TERM, paymentTerm);
        model.addAttribute(ATTR_MILESTONES, milestoneService.findByProjectId(project.getId()));
        model.addAttribute(ATTR_TRIGGERS, PaymentTrigger.values());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PROJECTS);
        return VIEW_FORM;
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable String projectCode,
            @PathVariable UUID id,
            @Valid @ModelAttribute(ATTR_PAYMENT_TERM) ProjectPaymentTerm paymentTerm,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            Project project = projectService.findByCode(projectCode);
            paymentTerm.setId(id);
            model.addAttribute(ATTR_PROJECT, project);
            model.addAttribute(ATTR_MILESTONES, milestoneService.findByProjectId(project.getId()));
            model.addAttribute(ATTR_TRIGGERS, PaymentTrigger.values());
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PROJECTS);
            return VIEW_FORM;
        }

        try {
            paymentTermService.update(id, paymentTerm);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Termin pembayaran berhasil diperbarui");
            return REDIRECT_PROJECT_PREFIX + projectCode;
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("sequence", "duplicate", e.getMessage());
            Project project = projectService.findByCode(projectCode);
            paymentTerm.setId(id);
            model.addAttribute(ATTR_PROJECT, project);
            model.addAttribute(ATTR_MILESTONES, milestoneService.findByProjectId(project.getId()));
            model.addAttribute(ATTR_TRIGGERS, PaymentTrigger.values());
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PROJECTS);
            return VIEW_FORM;
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(
            @PathVariable String projectCode,
            @PathVariable UUID id,
            RedirectAttributes redirectAttributes) {

        paymentTermService.delete(id);
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Termin pembayaran berhasil dihapus");
        return REDIRECT_PROJECT_PREFIX + projectCode;
    }

    @PostMapping("/{id}/generate-invoice")
    public String generateInvoice(
            @PathVariable String projectCode,
            @PathVariable UUID id,
            RedirectAttributes redirectAttributes) {

        try {
            var invoice = invoiceService.createFromPaymentTerm(id);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Invoice berhasil dibuat dari termin pembayaran");
            return "redirect:/invoices/" + invoice.getInvoiceNumber();
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return REDIRECT_PROJECT_PREFIX + projectCode;
        }
    }
}
