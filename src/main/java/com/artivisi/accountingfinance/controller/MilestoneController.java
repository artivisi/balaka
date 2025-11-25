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

@Controller
@RequestMapping("/projects/{projectId}/milestones")
@RequiredArgsConstructor
public class MilestoneController {

    private final ProjectMilestoneService milestoneService;
    private final ProjectService projectService;

    @GetMapping("/new")
    public String newForm(@PathVariable UUID projectId, Model model) {
        Project project = projectService.findById(projectId);
        ProjectMilestone milestone = new ProjectMilestone();

        model.addAttribute("project", project);
        model.addAttribute("milestone", milestone);
        model.addAttribute("currentPage", "projects");
        return "milestones/form";
    }

    @PostMapping("/new")
    public String create(
            @PathVariable UUID projectId,
            @Valid @ModelAttribute("milestone") ProjectMilestone milestone,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            Project project = projectService.findById(projectId);
            model.addAttribute("project", project);
            model.addAttribute("currentPage", "projects");
            return "milestones/form";
        }

        try {
            milestoneService.create(projectId, milestone);
            redirectAttributes.addFlashAttribute("successMessage", "Milestone berhasil ditambahkan");
            return "redirect:/projects/" + projectId;
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("sequence", "duplicate", e.getMessage());
            Project project = projectService.findById(projectId);
            model.addAttribute("project", project);
            model.addAttribute("currentPage", "projects");
            return "milestones/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(
            @PathVariable UUID projectId,
            @PathVariable UUID id,
            Model model) {

        Project project = projectService.findById(projectId);
        ProjectMilestone milestone = milestoneService.findById(id);

        model.addAttribute("project", project);
        model.addAttribute("milestone", milestone);
        model.addAttribute("currentPage", "projects");
        return "milestones/form";
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable UUID projectId,
            @PathVariable UUID id,
            @Valid @ModelAttribute("milestone") ProjectMilestone milestone,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            Project project = projectService.findById(projectId);
            milestone.setId(id);
            model.addAttribute("project", project);
            model.addAttribute("currentPage", "projects");
            return "milestones/form";
        }

        try {
            milestoneService.update(id, milestone);
            redirectAttributes.addFlashAttribute("successMessage", "Milestone berhasil diperbarui");
            return "redirect:/projects/" + projectId;
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("sequence", "duplicate", e.getMessage());
            Project project = projectService.findById(projectId);
            milestone.setId(id);
            model.addAttribute("project", project);
            model.addAttribute("currentPage", "projects");
            return "milestones/form";
        }
    }

    @PostMapping("/{id}/start")
    public String start(
            @PathVariable UUID projectId,
            @PathVariable UUID id,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            RedirectAttributes redirectAttributes) {

        milestoneService.startMilestone(id);

        if ("true".equals(hxRequest)) {
            return "redirect:/projects/" + projectId + "/milestones-fragment";
        }

        redirectAttributes.addFlashAttribute("successMessage", "Milestone dimulai");
        return "redirect:/projects/" + projectId;
    }

    @PostMapping("/{id}/complete")
    public String complete(
            @PathVariable UUID projectId,
            @PathVariable UUID id,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            RedirectAttributes redirectAttributes) {

        milestoneService.completeMilestone(id);

        if ("true".equals(hxRequest)) {
            return "redirect:/projects/" + projectId + "/milestones-fragment";
        }

        redirectAttributes.addFlashAttribute("successMessage", "Milestone selesai");
        return "redirect:/projects/" + projectId;
    }

    @PostMapping("/{id}/reset")
    public String reset(
            @PathVariable UUID projectId,
            @PathVariable UUID id,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            RedirectAttributes redirectAttributes) {

        milestoneService.resetMilestone(id);

        if ("true".equals(hxRequest)) {
            return "redirect:/projects/" + projectId + "/milestones-fragment";
        }

        redirectAttributes.addFlashAttribute("successMessage", "Milestone direset");
        return "redirect:/projects/" + projectId;
    }

    @PostMapping("/{id}/delete")
    public String delete(
            @PathVariable UUID projectId,
            @PathVariable UUID id,
            RedirectAttributes redirectAttributes) {

        milestoneService.delete(id);
        redirectAttributes.addFlashAttribute("successMessage", "Milestone berhasil dihapus");
        return "redirect:/projects/" + projectId;
    }
}
