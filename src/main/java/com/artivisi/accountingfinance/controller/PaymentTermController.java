package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.Project;
import com.artivisi.accountingfinance.entity.ProjectMilestone;
import com.artivisi.accountingfinance.entity.ProjectPaymentTerm;
import com.artivisi.accountingfinance.enums.PaymentTrigger;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.InvoiceService;
import com.artivisi.accountingfinance.service.ProjectMilestoneService;
import com.artivisi.accountingfinance.service.ProjectPaymentTermService;
import com.artivisi.accountingfinance.service.ProjectService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.BeanUtils;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static com.artivisi.accountingfinance.controller.ViewConstants.*;

@Controller
@RequestMapping("/projects/{projectCode}/payment-terms")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permission.PROJECT_VIEW + "')")
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

    @Getter
    @Setter
    static class MilestoneRef {
        private UUID id;
    }

    @Getter
    @Setter
    static class PaymentTermForm {
        private UUID id;

        @Min(value = 1, message = "Urutan minimal 1")
        private Integer sequence;

        @NotBlank(message = "Nama termin wajib diisi")
        @Size(max = 255, message = "Nama termin maksimal 255 karakter")
        private String name;

        @NotNull(message = "Pemicu pembayaran wajib diisi")
        private PaymentTrigger dueTrigger;

        private BigDecimal percentage;
        private BigDecimal amount;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate dueDate;

        // Nested ref for th:field="*{milestone.id}"
        private MilestoneRef milestone = new MilestoneRef();
    }

    private ProjectPaymentTerm toEntity(PaymentTermForm form) {
        ProjectPaymentTerm entity = new ProjectPaymentTerm();
        BeanUtils.copyProperties(form, entity, "id", "milestone");
        if (form.getMilestone() != null && form.getMilestone().getId() != null) {
            ProjectMilestone milestoneEntity = new ProjectMilestone();
            milestoneEntity.setId(form.getMilestone().getId());
            entity.setMilestone(milestoneEntity);
        } else {
            entity.setMilestone(null);
        }
        return entity;
    }

    private PaymentTermForm toForm(ProjectPaymentTerm entity) {
        PaymentTermForm form = new PaymentTermForm();
        BeanUtils.copyProperties(entity, form, "milestone");
        if (entity.getMilestone() != null) {
            MilestoneRef ref = new MilestoneRef();
            ref.setId(entity.getMilestone().getId());
            form.setMilestone(ref);
        }
        return form;
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('" + Permission.PROJECT_EDIT + "')")
    public String newForm(@PathVariable String projectCode, Model model) {
        Project project = projectService.findByCode(projectCode);
        PaymentTermForm form = new PaymentTermForm();
        form.setMilestone(new MilestoneRef());

        model.addAttribute(ATTR_PROJECT, project);
        model.addAttribute(ATTR_PAYMENT_TERM, form);
        model.addAttribute(ATTR_MILESTONES, milestoneService.findByProjectId(project.getId()));
        model.addAttribute(ATTR_TRIGGERS, PaymentTrigger.values());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PROJECTS);
        return VIEW_FORM;
    }

    @PostMapping("/new")
    @PreAuthorize("hasAuthority('" + Permission.PROJECT_EDIT + "')")
    public String create(
            @PathVariable String projectCode,
            @Valid @ModelAttribute(ATTR_PAYMENT_TERM) PaymentTermForm form,
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
            ProjectPaymentTerm paymentTerm = toEntity(form);
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
    @PreAuthorize("hasAuthority('" + Permission.PROJECT_EDIT + "')")
    public String editForm(
            @PathVariable String projectCode,
            @PathVariable UUID id,
            Model model) {

        Project project = projectService.findByCode(projectCode);
        ProjectPaymentTerm existing = paymentTermService.findById(id);

        model.addAttribute(ATTR_PROJECT, project);
        model.addAttribute(ATTR_PAYMENT_TERM, toForm(existing));
        model.addAttribute(ATTR_MILESTONES, milestoneService.findByProjectId(project.getId()));
        model.addAttribute(ATTR_TRIGGERS, PaymentTrigger.values());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PROJECTS);
        return VIEW_FORM;
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permission.PROJECT_EDIT + "')")
    public String update(
            @PathVariable String projectCode,
            @PathVariable UUID id,
            @Valid @ModelAttribute(ATTR_PAYMENT_TERM) PaymentTermForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            Project project = projectService.findByCode(projectCode);
            form.setId(id);
            model.addAttribute(ATTR_PROJECT, project);
            model.addAttribute(ATTR_MILESTONES, milestoneService.findByProjectId(project.getId()));
            model.addAttribute(ATTR_TRIGGERS, PaymentTrigger.values());
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PROJECTS);
            return VIEW_FORM;
        }

        try {
            ProjectPaymentTerm updated = toEntity(form);
            paymentTermService.update(id, updated);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Termin pembayaran berhasil diperbarui");
            return REDIRECT_PROJECT_PREFIX + projectCode;
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("sequence", "duplicate", e.getMessage());
            Project project = projectService.findByCode(projectCode);
            form.setId(id);
            model.addAttribute(ATTR_PROJECT, project);
            model.addAttribute(ATTR_MILESTONES, milestoneService.findByProjectId(project.getId()));
            model.addAttribute(ATTR_TRIGGERS, PaymentTrigger.values());
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PROJECTS);
            return VIEW_FORM;
        }
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('" + Permission.PROJECT_EDIT + "')")
    public String delete(
            @PathVariable String projectCode,
            @PathVariable UUID id,
            RedirectAttributes redirectAttributes) {

        paymentTermService.delete(id);
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Termin pembayaran berhasil dihapus");
        return REDIRECT_PROJECT_PREFIX + projectCode;
    }

    @PostMapping("/{id}/generate-invoice")
    @PreAuthorize("hasAuthority('" + Permission.INVOICE_CREATE + "')")
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
