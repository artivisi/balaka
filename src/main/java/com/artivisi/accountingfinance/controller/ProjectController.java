package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.Project;
import com.artivisi.accountingfinance.enums.ProjectStatus;
import com.artivisi.accountingfinance.service.ClientService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/projects")
@RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasAuthority('" + com.artivisi.accountingfinance.security.Permission.PROJECT_VIEW + "')")
public class ProjectController {

    private final ProjectService projectService;
    private final ClientService clientService;

    @GetMapping
    public String list(
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) String search,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            @PageableDefault(size = 20) Pageable pageable,
            Model model) {

        Page<Project> projects = projectService.findByFilters(status, clientId, search, pageable);

        model.addAttribute("projects", projects);
        model.addAttribute("status", status);
        model.addAttribute("clientId", clientId);
        model.addAttribute("search", search);
        model.addAttribute("statuses", ProjectStatus.values());
        model.addAttribute("clients", clientService.findActiveClients());
        model.addAttribute("currentPage", "projects");

        if ("true".equals(hxRequest)) {
            return "projects/fragments/project-table :: table";
        }

        return "projects/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("project", new Project());
        model.addAttribute("clients", clientService.findActiveClients());
        model.addAttribute("currentPage", "projects");
        return "projects/form";
    }

    @PostMapping("/new")
    public String create(
            @Valid @ModelAttribute("project") Project project,
            BindingResult bindingResult,
            @RequestParam(required = false) UUID clientId,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("clients", clientService.findActiveClients());
            model.addAttribute("currentPage", "projects");
            return "projects/form";
        }

        try {
            Project saved = projectService.create(project, clientId);
            redirectAttributes.addFlashAttribute("successMessage", "Proyek berhasil ditambahkan");
            return "redirect:/projects/" + saved.getCode();
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("code", "duplicate", e.getMessage());
            model.addAttribute("clients", clientService.findActiveClients());
            model.addAttribute("currentPage", "projects");
            return "projects/form";
        }
    }

    @GetMapping("/{code}")
    public String detail(@PathVariable String code, Model model) {
        Project project = projectService.findByCode(code);
        model.addAttribute("project", project);
        model.addAttribute("currentPage", "projects");
        return "projects/detail";
    }

    @GetMapping("/{code}/edit")
    public String editForm(@PathVariable String code, Model model) {
        Project project = projectService.findByCode(code);
        model.addAttribute("project", project);
        model.addAttribute("clients", clientService.findActiveClients());
        model.addAttribute("currentPage", "projects");
        return "projects/form";
    }

    @PostMapping("/{code}")
    public String update(
            @PathVariable String code,
            @Valid @ModelAttribute("project") Project project,
            BindingResult bindingResult,
            @RequestParam(required = false) UUID clientId,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            Project existing = projectService.findByCode(code);
            project.setId(existing.getId());
            model.addAttribute("clients", clientService.findActiveClients());
            model.addAttribute("currentPage", "projects");
            return "projects/form";
        }

        try {
            Project existing = projectService.findByCode(code);
            projectService.update(existing.getId(), project, clientId);
            redirectAttributes.addFlashAttribute("successMessage", "Proyek berhasil diperbarui");
            return "redirect:/projects/" + project.getCode();
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("code", "duplicate", e.getMessage());
            Project existing = projectService.findByCode(code);
            project.setId(existing.getId());
            model.addAttribute("clients", clientService.findActiveClients());
            model.addAttribute("currentPage", "projects");
            return "projects/form";
        }
    }

    @PostMapping("/{code}/complete")
    public String complete(
            @PathVariable String code,
            RedirectAttributes redirectAttributes) {

        Project project = projectService.findByCode(code);
        projectService.complete(project.getId());
        redirectAttributes.addFlashAttribute("successMessage", "Proyek berhasil diselesaikan");
        return "redirect:/projects/" + code;
    }

    @PostMapping("/{code}/archive")
    public String archive(
            @PathVariable String code,
            RedirectAttributes redirectAttributes) {

        Project project = projectService.findByCode(code);
        projectService.archive(project.getId());
        redirectAttributes.addFlashAttribute("successMessage", "Proyek berhasil diarsipkan");
        return "redirect:/projects/" + code;
    }

    @PostMapping("/{code}/reactivate")
    public String reactivate(
            @PathVariable String code,
            RedirectAttributes redirectAttributes) {

        Project project = projectService.findByCode(code);
        projectService.reactivate(project.getId());
        redirectAttributes.addFlashAttribute("successMessage", "Proyek berhasil diaktifkan kembali");
        return "redirect:/projects/" + code;
    }
}
