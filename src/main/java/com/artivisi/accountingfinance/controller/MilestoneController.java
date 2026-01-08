package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.Project;
import com.artivisi.accountingfinance.entity.ProjectMilestone;
import com.artivisi.accountingfinance.service.ProjectMilestoneService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

import static com.artivisi.accountingfinance.controller.ViewConstants.*;

@Controller
@RequestMapping("/projects/{projectCode}/milestones")
@RequiredArgsConstructor
public class MilestoneController {

    private static final String REDIRECT_PROJECT_PREFIX = "redirect:/projects/";
    private static final String MILESTONES_FRAGMENT_SUFFIX = "/milestones-fragment";
    private static final String VIEW_FORM = "milestones/form";
    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";

    private final ProjectMilestoneService milestoneService;
    private final ProjectService projectService;

    @GetMapping("/new")
    public String newForm(@PathVariable String projectCode, Model model) {
        Project project = projectService.findByCode(projectCode);
        ProjectMilestone milestone = new ProjectMilestone();

        model.addAttribute("project", project);
        model.addAttribute("milestone", milestone);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PROJECTS);
        return VIEW_FORM;
    }

    @PostMapping("/new")
    public String create(
            @PathVariable String projectCode,
            @Valid @ModelAttribute("milestone") ProjectMilestone milestone,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            Project project = projectService.findByCode(projectCode);
            model.addAttribute("project", project);
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PROJECTS);
            return VIEW_FORM;
        }

        try {
            Project project = projectService.findByCode(projectCode);
            milestoneService.create(project.getId(), milestone);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Milestone berhasil ditambahkan");
            return REDIRECT_PROJECT_PREFIX + projectCode;
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("sequence", "duplicate", e.getMessage());
            Project project = projectService.findByCode(projectCode);
            model.addAttribute("project", project);
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
        ProjectMilestone milestone = milestoneService.findById(id);

        model.addAttribute("project", project);
        model.addAttribute("milestone", milestone);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PROJECTS);
        return VIEW_FORM;
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable String projectCode,
            @PathVariable UUID id,
            @Valid @ModelAttribute("milestone") ProjectMilestone milestone,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            Project project = projectService.findByCode(projectCode);
            milestone.setId(id);
            model.addAttribute("project", project);
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PROJECTS);
            return VIEW_FORM;
        }

        try {
            milestoneService.update(id, milestone);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Milestone berhasil diperbarui");
            return REDIRECT_PROJECT_PREFIX + projectCode;
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("sequence", "duplicate", e.getMessage());
            Project project = projectService.findByCode(projectCode);
            milestone.setId(id);
            model.addAttribute("project", project);
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PROJECTS);
            return VIEW_FORM;
        }
    }

    @PostMapping("/{id}/start")
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
    public String delete(
            @PathVariable String projectCode,
            @PathVariable UUID id,
            RedirectAttributes redirectAttributes) {

        milestoneService.delete(id);
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Milestone berhasil dihapus");
        return REDIRECT_PROJECT_PREFIX + projectCode;
    }
}
