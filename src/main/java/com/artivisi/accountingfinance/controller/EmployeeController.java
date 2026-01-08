package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.Employee;
import com.artivisi.accountingfinance.entity.EmploymentStatus;
import com.artivisi.accountingfinance.entity.EmploymentType;
import com.artivisi.accountingfinance.entity.PtkpStatus;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

import static com.artivisi.accountingfinance.controller.ViewConstants.*;

@Controller
@RequestMapping("/employees")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permission.EMPLOYEE_VIEW + "')")
public class EmployeeController {

    private static final String ATTR_EMPLOYEE = "employee";
    private static final String ATTR_EMPLOYMENT_STATUSES = "employmentStatuses";
    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";
    private static final String REDIRECT_EMPLOYEES = "redirect:/employees";
    private static final String VIEW_FORM = "employees/form";

    private final EmployeeService employeeService;
    private final UserRepository userRepository;

    @GetMapping
    public String list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) EmploymentStatus status,
            @RequestParam(required = false) Boolean active,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            @PageableDefault(size = 20) Pageable pageable,
            Model model) {

        Page<Employee> employees = employeeService.findByFilters(search, status, active, pageable);

        model.addAttribute("employees", employees);
        model.addAttribute("search", search);
        model.addAttribute("status", status);
        model.addAttribute("active", active);
        model.addAttribute(ATTR_EMPLOYMENT_STATUSES, EmploymentStatus.values());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_EMPLOYEES);

        if ("true".equals(hxRequest)) {
            return "employees/fragments/employee-table :: table";
        }

        return "employees/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('" + Permission.EMPLOYEE_CREATE + "')")
    public String newForm(Model model) {
        Employee employee = new Employee();
        employee.setEmploymentStatus(EmploymentStatus.ACTIVE);
        employee.setEmploymentType(EmploymentType.PERMANENT);
        employee.setPtkpStatus(PtkpStatus.TK_0);

        model.addAttribute(ATTR_EMPLOYEE, employee);
        model.addAttribute("ptkpStatuses", PtkpStatus.values());
        model.addAttribute("employmentTypes", EmploymentType.values());
        model.addAttribute(ATTR_EMPLOYMENT_STATUSES, EmploymentStatus.values());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_EMPLOYEES);
        return VIEW_FORM;
    }

    @PostMapping("/new")
    @PreAuthorize("hasAuthority('" + Permission.EMPLOYEE_CREATE + "')")
    public String create(
            @Valid @ModelAttribute("employee") Employee employee,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            addFormAttributes(model);
            return VIEW_FORM;
        }

        try {
            Employee saved = employeeService.create(employee);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Karyawan berhasil ditambahkan");
            return REDIRECT_EMPLOYEES + "/" + saved.getEmployeeId();
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("NIK")) {
                bindingResult.rejectValue("employeeId", "duplicate", e.getMessage());
            } else if (e.getMessage().contains("NPWP")) {
                bindingResult.rejectValue("npwp", "duplicate", e.getMessage());
            } else {
                bindingResult.reject("error", e.getMessage());
            }
            addFormAttributes(model);
            return VIEW_FORM;
        }
    }

    @GetMapping("/{employeeId}")
    public String detail(@PathVariable String employeeId, Model model) {
        Employee employee = employeeService.findByEmployeeId(employeeId);
        model.addAttribute(ATTR_EMPLOYEE, employee);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_EMPLOYEES);
        return "employees/detail";
    }

    @GetMapping("/{employeeId}/edit")
    @PreAuthorize("hasAuthority('" + Permission.EMPLOYEE_EDIT + "')")
    public String editForm(@PathVariable String employeeId, Model model) {
        Employee employee = employeeService.findByEmployeeId(employeeId);
        model.addAttribute(ATTR_EMPLOYEE, employee);
        addFormAttributes(model);
        return VIEW_FORM;
    }

    @PostMapping("/{employeeId}")
    @PreAuthorize("hasAuthority('" + Permission.EMPLOYEE_EDIT + "')")
    public String update(
            @PathVariable String employeeId,
            @Valid @ModelAttribute("employee") Employee employee,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            Employee existing = employeeService.findByEmployeeId(employeeId);
            employee.setId(existing.getId());
            addFormAttributes(model);
            return VIEW_FORM;
        }

        try {
            Employee existing = employeeService.findByEmployeeId(employeeId);
            employeeService.update(existing.getId(), employee);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Karyawan berhasil diperbarui");
            return REDIRECT_EMPLOYEES + "/" + employee.getEmployeeId();
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("NIK")) {
                bindingResult.rejectValue("employeeId", "duplicate", e.getMessage());
            } else if (e.getMessage().contains("NPWP")) {
                bindingResult.rejectValue("npwp", "duplicate", e.getMessage());
            } else {
                bindingResult.reject("error", e.getMessage());
            }
            Employee existing = employeeService.findByEmployeeId(employeeId);
            employee.setId(existing.getId());
            addFormAttributes(model);
            return VIEW_FORM;
        }
    }

    @PostMapping("/{employeeId}/deactivate")
    @PreAuthorize("hasAuthority('" + Permission.EMPLOYEE_EDIT + "')")
    public String deactivate(
            @PathVariable String employeeId,
            RedirectAttributes redirectAttributes) {

        Employee employee = employeeService.findByEmployeeId(employeeId);
        employeeService.deactivate(employee.getId());
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Karyawan berhasil dinonaktifkan");
        return REDIRECT_EMPLOYEES + "/" + employeeId;
    }

    @PostMapping("/{employeeId}/activate")
    @PreAuthorize("hasAuthority('" + Permission.EMPLOYEE_EDIT + "')")
    public String activate(
            @PathVariable String employeeId,
            RedirectAttributes redirectAttributes) {

        Employee employee = employeeService.findByEmployeeId(employeeId);
        employeeService.activate(employee.getId());
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Karyawan berhasil diaktifkan");
        return REDIRECT_EMPLOYEES + "/" + employeeId;
    }

    private void addFormAttributes(Model model) {
        model.addAttribute("ptkpStatuses", PtkpStatus.values());
        model.addAttribute("employmentTypes", EmploymentType.values());
        model.addAttribute(ATTR_EMPLOYMENT_STATUSES, EmploymentStatus.values());
        model.addAttribute("users", userRepository.findByActiveTrue());
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_EMPLOYEES);
    }
}
