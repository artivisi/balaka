package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.SalaryComponent;
import com.artivisi.accountingfinance.util.FormUtils;
import com.artivisi.accountingfinance.entity.SalaryComponentType;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.SalaryComponentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.UUID;

import static com.artivisi.accountingfinance.controller.ViewConstants.*;

@Controller
@RequestMapping("/salary-components")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permission.SALARY_COMPONENT_VIEW + "')")
public class SalaryComponentController {

    private static final String ATTR_COMPONENT = "component";
    private static final String ATTR_COMPONENT_TYPES = "componentTypes";
    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";
    private static final String REDIRECT_SALARY_COMPONENTS = "redirect:/salary-components";
    private static final String VIEW_FORM = "salary-components/form";
    private static final String HAS_MANAGE = "hasAuthority('" + Permission.SALARY_COMPONENT_MANAGE + "')";

    private final SalaryComponentService salaryComponentService;

    @Getter
    @Setter
    static class SalaryComponentForm {
        private UUID id;

        @NotBlank(message = "Kode komponen wajib diisi")
        @Size(max = 20, message = "Kode komponen maksimal 20 karakter")
        private String code;

        @NotBlank(message = "Nama komponen wajib diisi")
        @Size(max = 100, message = "Nama komponen maksimal 100 karakter")
        private String name;

        @Size(max = 255, message = "Deskripsi maksimal 255 karakter")
        private String description;

        @NotNull(message = "Tipe komponen wajib diisi")
        private SalaryComponentType componentType;

        private Boolean isPercentage;

        private BigDecimal defaultRate;

        private BigDecimal defaultAmount;

        private Integer displayOrder;

        private Boolean isTaxable;

        @Size(max = 50, message = "Kategori BPJS maksimal 50 karakter")
        private String bpjsCategory;
    }

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
    @PreAuthorize(HAS_MANAGE)
    public String newForm(Model model) {
        SalaryComponentForm form = new SalaryComponentForm();
        form.setComponentType(SalaryComponentType.EARNING);
        form.setIsPercentage(false);
        form.setIsTaxable(true);

        model.addAttribute(ATTR_COMPONENT, form);
        model.addAttribute(ATTR_COMPONENT_TYPES, SalaryComponentType.values());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_SALARY_COMPONENTS);
        return VIEW_FORM;
    }

    @PostMapping("/new")
    @PreAuthorize(HAS_MANAGE)
    public String create(
            @Valid @ModelAttribute("component") SalaryComponentForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            addFormAttributes(model);
            return VIEW_FORM;
        }

        try {
            SalaryComponent component = new SalaryComponent();
            BeanUtils.copyProperties(form, component, "id", "isPercentage", "isTaxable", "bpjsCategory");
            component.setIsPercentage(FormUtils.checkboxValue(form.getIsPercentage()));
            component.setIsTaxable(FormUtils.checkboxValue(form.getIsTaxable()));
            component.setBpjsCategory(StringUtils.hasText(form.getBpjsCategory()) ? form.getBpjsCategory() : null);
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
    @PreAuthorize(HAS_MANAGE)
    public String editForm(@PathVariable UUID id, Model model) {
        SalaryComponent component = salaryComponentService.findById(id);

        SalaryComponentForm form = new SalaryComponentForm();
        BeanUtils.copyProperties(component, form);

        model.addAttribute(ATTR_COMPONENT, form);
        addFormAttributes(model);
        return VIEW_FORM;
    }

    @PostMapping("/{id}")
    @PreAuthorize(HAS_MANAGE)
    public String update(
            @PathVariable UUID id,
            @Valid @ModelAttribute("component") SalaryComponentForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            form.setId(id);
            addFormAttributes(model);
            return VIEW_FORM;
        }

        try {
            SalaryComponent component = new SalaryComponent();
            BeanUtils.copyProperties(form, component, "id", "isPercentage", "isTaxable", "bpjsCategory");
            component.setIsPercentage(FormUtils.checkboxValue(form.getIsPercentage()));
            component.setIsTaxable(FormUtils.checkboxValue(form.getIsTaxable()));
            component.setBpjsCategory(StringUtils.hasText(form.getBpjsCategory()) ? form.getBpjsCategory() : null);
            salaryComponentService.update(id, component);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Komponen gaji berhasil diperbarui");
            return REDIRECT_SALARY_COMPONENTS + "/" + id;
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("Kode")) {
                bindingResult.rejectValue("code", "duplicate", e.getMessage());
            } else {
                bindingResult.reject("error", e.getMessage());
            }
            form.setId(id);
            addFormAttributes(model);
            return VIEW_FORM;
        }
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize(HAS_MANAGE)
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
    @PreAuthorize(HAS_MANAGE)
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
