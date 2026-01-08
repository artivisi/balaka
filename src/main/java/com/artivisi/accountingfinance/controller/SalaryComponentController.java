package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.SalaryComponent;
import com.artivisi.accountingfinance.entity.SalaryComponentType;
import com.artivisi.accountingfinance.service.SalaryComponentService;
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
@RequestMapping("/salary-components")
@RequiredArgsConstructor
@org.springframework.security.access.prepost.PreAuthorize("hasAuthority('" + com.artivisi.accountingfinance.security.Permission.SALARY_COMPONENT_VIEW + "')")
public class SalaryComponentController {

    private static final String ATTR_COMPONENT = "component";
    private static final String ATTR_COMPONENT_TYPES = "componentTypes";
    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";
    private static final String REDIRECT_SALARY_COMPONENTS = "redirect:/salary-components";
    private static final String VIEW_FORM = "salary-components/form";

    private final SalaryComponentService salaryComponentService;

    @GetMapping
    public String list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) SalaryComponentType type,
            @RequestParam(required = false) Boolean active,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            @PageableDefault(size = 20) Pageable pageable,
            Model model) {

        Page<SalaryComponent> components = salaryComponentService.findByFilters(search, type, active, pageable);

        model.addAttribute("components", components);
        model.addAttribute("search", search);
        model.addAttribute("type", type);
        model.addAttribute("active", active);
        model.addAttribute(ATTR_COMPONENT_TYPES, SalaryComponentType.values());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_SALARY_COMPONENTS);

        if ("true".equals(hxRequest)) {
            return "salary-components/fragments/component-table :: table";
        }

        return "salary-components/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        SalaryComponent component = new SalaryComponent();
        component.setComponentType(SalaryComponentType.EARNING);
        component.setIsPercentage(false);
        component.setIsTaxable(true);

        model.addAttribute(ATTR_COMPONENT, component);
        model.addAttribute(ATTR_COMPONENT_TYPES, SalaryComponentType.values());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_SALARY_COMPONENTS);
        return VIEW_FORM;
    }

    @PostMapping("/new")
    public String create(
            @Valid @ModelAttribute("component") SalaryComponent component,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            addFormAttributes(model);
            return VIEW_FORM;
        }

        try {
            SalaryComponent saved = salaryComponentService.create(component);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Komponen gaji berhasil ditambahkan");
            return REDIRECT_SALARY_COMPONENTS + "/" + saved.getId();
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("Kode")) {
                bindingResult.rejectValue("code", "duplicate", e.getMessage());
            } else {
                bindingResult.reject("error", e.getMessage());
            }
            addFormAttributes(model);
            return VIEW_FORM;
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        SalaryComponent component = salaryComponentService.findById(id);
        model.addAttribute(ATTR_COMPONENT, component);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_SALARY_COMPONENTS);
        return "salary-components/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model) {
        SalaryComponent component = salaryComponentService.findById(id);
        model.addAttribute(ATTR_COMPONENT, component);
        addFormAttributes(model);
        return VIEW_FORM;
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable UUID id,
            @Valid @ModelAttribute("component") SalaryComponent component,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            component.setId(id);
            addFormAttributes(model);
            return VIEW_FORM;
        }

        try {
            salaryComponentService.update(id, component);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Komponen gaji berhasil diperbarui");
            return REDIRECT_SALARY_COMPONENTS + "/" + id;
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("Kode")) {
                bindingResult.rejectValue("code", "duplicate", e.getMessage());
            } else {
                bindingResult.reject("error", e.getMessage());
            }
            component.setId(id);
            addFormAttributes(model);
            return VIEW_FORM;
        }
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(
            @PathVariable UUID id,
            RedirectAttributes redirectAttributes) {

        try {
            salaryComponentService.deactivate(id);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Komponen gaji berhasil dinonaktifkan");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return REDIRECT_SALARY_COMPONENTS + "/" + id;
    }

    @PostMapping("/{id}/activate")
    public String activate(
            @PathVariable UUID id,
            RedirectAttributes redirectAttributes) {

        salaryComponentService.activate(id);
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Komponen gaji berhasil diaktifkan");
        return REDIRECT_SALARY_COMPONENTS + "/" + id;
    }

    private void addFormAttributes(Model model) {
        model.addAttribute(ATTR_COMPONENT_TYPES, SalaryComponentType.values());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_SALARY_COMPONENTS);
    }
}
