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

import static com.artivisi.accountingfinance.controller.ViewConstants.*;

@Controller
@RequestMapping("/projects")
@RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasAuthority('" + com.artivisi.accountingfinance.security.Permission.PROJECT_VIEW + "')")
public class ProjectController {

    private static final String ATTR_PROJECT = "project";
    private static final String ATTR_CLIENTS = "clients";
    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";
    private static final String REDIRECT_PROJECTS_PREFIX = "redirect:/projects/";
    private static final String VIEW_FORM = "projects/form";

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
        model.addAttribute(ATTR_CLIENTS, clientService.findActiveClients());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PROJECTS);

        if ("true".equals(hxRequest)) {
            return "projects/fragments/project-table :: table";
        }

        return "projects/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute(ATTR_PROJECT, new Project());
        model.addAttribute(ATTR_CLIENTS, clientService.findActiveClients());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PROJECTS);
        return VIEW_FORM;
    }

    @PostMapping("/new")
    public String create(
            @Valid @ModelAttribute(ATTR_PROJECT) Project project,
            BindingResult bindingResult,
            @RequestParam(required = false) UUID clientId,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute(ATTR_CLIENTS, clientService.findActiveClients());
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PROJECTS);
            return VIEW_FORM;
        }

        try {
            Project saved = projectService.create(project, clientId);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Proyek berhasil ditambahkan");
            return REDIRECT_PROJECTS_PREFIX + saved.getCode();
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("code", "duplicate", e.getMessage());
            model.addAttribute(ATTR_CLIENTS, clientService.findActiveClients());
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PROJECTS);
            return VIEW_FORM;
        }
    }

    @GetMapping("/{code}")
    public String detail(@PathVariable String code, Model model) {
        Project project = projectService.findByCode(code);
        model.addAttribute(ATTR_PROJECT, project);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PROJECTS);
        return "projects/detail";
    }

    @GetMapping("/{code}/edit")
    public String editForm(@PathVariable String code, Model model) {
        Project project = projectService.findByCode(code);
        model.addAttribute(ATTR_PROJECT, project);
        model.addAttribute(ATTR_CLIENTS, clientService.findActiveClients());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PROJECTS);
        return VIEW_FORM;
    }

    @PostMapping("/{code}")
    public String update(
            @PathVariable String code,
            @Valid @ModelAttribute(ATTR_PROJECT) Project project,
            BindingResult bindingResult,
            @RequestParam(required = false) UUID clientId,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            Project existing = projectService.findByCode(code);
            project.setId(existing.getId());
            model.addAttribute(ATTR_CLIENTS, clientService.findActiveClients());
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PROJECTS);
            return VIEW_FORM;
        }

        try {
            Project existing = projectService.findByCode(code);
            projectService.update(existing.getId(), project, clientId);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Proyek berhasil diperbarui");
            return REDIRECT_PROJECTS_PREFIX + project.getCode();
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("code", "duplicate", e.getMessage());
            Project existing = projectService.findByCode(code);
            project.setId(existing.getId());
            model.addAttribute(ATTR_CLIENTS, clientService.findActiveClients());
            model.addAttribute(ATTR_CURRENT_PAGE, PAGE_PROJECTS);
            return VIEW_FORM;
        }
    }

    @PostMapping("/{code}/complete")
    public String complete(
            @PathVariable String code,
            RedirectAttributes redirectAttributes) {

        Project project = projectService.findByCode(code);
        projectService.complete(project.getId());
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Proyek berhasil diselesaikan");
        return REDIRECT_PROJECTS_PREFIX + code;
    }

    @PostMapping("/{code}/archive")
    public String archive(
            @PathVariable String code,
            RedirectAttributes redirectAttributes) {

        Project project = projectService.findByCode(code);
        projectService.archive(project.getId());
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Proyek berhasil diarsipkan");
        return REDIRECT_PROJECTS_PREFIX + code;
    }

    @PostMapping("/{code}/reactivate")
    public String reactivate(
            @PathVariable String code,
            RedirectAttributes redirectAttributes) {

        Project project = projectService.findByCode(code);
        projectService.reactivate(project.getId());
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Proyek berhasil diaktifkan kembali");
        return REDIRECT_PROJECTS_PREFIX + code;
    }
}
