package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.Project;
import com.artivisi.accountingfinance.entity.ProjectMilestone;
import com.artivisi.accountingfinance.enums.MilestoneStatus;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.ProjectMilestoneService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.UUID;

import static com.artivisi.accountingfinance.controller.ViewConstants.*;

@Controller
@RequestMapping("/projects/{projectCode}/milestones")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permission.PROJECT_VIEW + "')")
public class MilestoneController {

    private static final String REDIRECT_PROJECT_PREFIX = "redirect:/projects/";
    private static final String MILESTONES_FRAGMENT_SUFFIX = "/milestones-fragment";
    private static final String VIEW_FORM = "milestones/form";
    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";
    private static final String ATTR_PROJECT = "project";

    private final ProjectMilestoneService milestoneService;
    private final ProjectService projectService;

    @Getter
    @Setter
    static class MilestoneForm {
        private UUID id;

        @Min(value = 1, message = "Urutan minimal 1")
        private Integer sequence;

        @NotBlank(message = "Nama milestone wajib diisi")
        @Size(max = 255, message = "Nama milestone maksimal 255 karakter")
        private String name;

        private String description;

        @NotNull(message = "Bobot wajib diisi")
        @Min(value = 0, message = "Bobot minimal 0")
        private Integer weightPercent;

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate targetDate;
        private MilestoneStatus status;
    }

    private ProjectMilestone toEntity(MilestoneForm form) {
        ProjectMilestone entity = new ProjectMilestone();
        BeanUtils.copyProperties(form, entity, "id");
        return entity;
    }

    private MilestoneForm toForm(ProjectMilestone entity) {
        MilestoneForm form = new MilestoneForm();
        BeanUtils.copyProperties(entity, form);
        return form;
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('" + Permission.PROJECT_EDIT + "')")
    public String newForm(@PathVariable String projectCode, Model model) {
        Project project = projectService.findByCode(projectCode);

        model.addAttribute(ATTR_PROJECT, project);
        model.addAttribute("milestone", new MilestoneForm());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PROJECTS);
        return VIEW_FORM;
    }

    @PostMapping("/new")
    @PreAuthorize("hasAuthority('" + Permission.PROJECT_EDIT + "')")
    public String create(
            @PathVariable String projectCode,
            @Valid @ModelAttribute("milestone") MilestoneForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            Project project = projectService.findByCode(projectCode);
            model.addAttribute(ATTR_PROJECT, project);
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PROJECTS);
            return VIEW_FORM;
        }

        try {
            Project project = projectService.findByCode(projectCode);
            ProjectMilestone milestone = toEntity(form);
            milestoneService.create(project.getId(), milestone);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Milestone berhasil ditambahkan");
            return REDIRECT_PROJECT_PREFIX + projectCode;
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("sequence", "duplicate", e.getMessage());
            Project project = projectService.findByCode(projectCode);
            model.addAttribute(ATTR_PROJECT, project);
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
        ProjectMilestone existing = milestoneService.findById(id);

        model.addAttribute(ATTR_PROJECT, project);
        model.addAttribute("milestone", toForm(existing));
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PROJECTS);
        return VIEW_FORM;
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permission.PROJECT_EDIT + "')")
    public String update(
            @PathVariable String projectCode,
            @PathVariable UUID id,
            @Valid @ModelAttribute("milestone") MilestoneForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (form.getSequence() == null) {
            bindingResult.rejectValue("sequence", "required", "Urutan wajib diisi");
        }

        if (bindingResult.hasErrors()) {
            Project project = projectService.findByCode(projectCode);
            form.setId(id);
            model.addAttribute(ATTR_PROJECT, project);
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PROJECTS);
            return VIEW_FORM;
        }

        try {
            ProjectMilestone updated = toEntity(form);
            milestoneService.update(id, updated);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Milestone berhasil diperbarui");
            return REDIRECT_PROJECT_PREFIX + projectCode;
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("sequence", "duplicate", e.getMessage());
            Project project = projectService.findByCode(projectCode);
            form.setId(id);
            model.addAttribute(ATTR_PROJECT, project);
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PROJECTS);
            return VIEW_FORM;
        }
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('" + Permission.PROJECT_EDIT + "')")
    public String start(
            @PathVariable String projectCode,
            @PathVariable UUID id,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            RedirectAttributes redirectAttributes) {

        milestoneService.startMilestone(id);

        if ("true".equals(hxRequest)) {
            return REDIRECT_PROJECT_PREFIX + projectCode + MILESTONES_FRAGMENT_SUFFIX;
        }

        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Milestone dimulai");
        return REDIRECT_PROJECT_PREFIX + projectCode;
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('" + Permission.PROJECT_EDIT + "')")
    public String complete(
            @PathVariable String projectCode,
            @PathVariable UUID id,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            RedirectAttributes redirectAttributes) {

        milestoneService.completeMilestone(id);

        if ("true".equals(hxRequest)) {
            return REDIRECT_PROJECT_PREFIX + projectCode + MILESTONES_FRAGMENT_SUFFIX;
        }

        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Milestone selesai");
        return REDIRECT_PROJECT_PREFIX + projectCode;
    }

    @PostMapping("/{id}/reset")
    @PreAuthorize("hasAuthority('" + Permission.PROJECT_EDIT + "')")
    public String reset(
            @PathVariable String projectCode,
            @PathVariable UUID id,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            RedirectAttributes redirectAttributes) {

        milestoneService.resetMilestone(id);

        if ("true".equals(hxRequest)) {
            return REDIRECT_PROJECT_PREFIX + projectCode + MILESTONES_FRAGMENT_SUFFIX;
        }

        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Milestone direset");
        return REDIRECT_PROJECT_PREFIX + projectCode;
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('" + Permission.PROJECT_EDIT + "')")
    public String delete(
            @PathVariable String projectCode,
            @PathVariable UUID id,
            RedirectAttributes redirectAttributes) {

        milestoneService.delete(id);
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Milestone berhasil dihapus");
        return REDIRECT_PROJECT_PREFIX + projectCode;
    }
}
