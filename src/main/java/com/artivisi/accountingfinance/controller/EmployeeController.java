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
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.UUID;

import static com.artivisi.accountingfinance.controller.ViewConstants.*;

@Controller
@RequestMapping("/employees")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permission.EMPLOYEE_VIEW + "')")
public class EmployeeController {

    private static final String ATTR_EMPLOYEE = "employee";
    private static final String ATTR_EDIT_EMPLOYEE_ID = "editEmployeeId";
    private static final String ATTR_EMPLOYMENT_STATUSES = "employmentStatuses";
    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";
    private static final String REDIRECT_EMPLOYEES = "redirect:/employees";
    private static final String VIEW_FORM = "employees/form";
    private static final String ERR_DUPLICATE = "duplicate";

    private final EmployeeService employeeService;
    private final UserRepository userRepository;

    @Getter
    @Setter
    static class EmployeeForm {
        private UUID id;

        @NotBlank(message = "NIK karyawan wajib diisi")
        @Size(max = 20, message = "NIK karyawan maksimal 20 karakter")
        private String employeeId;

        @NotBlank(message = "Nama karyawan wajib diisi")
        @Size(max = 255, message = "Nama karyawan maksimal 255 karakter")
        private String name;

        @Email(message = "Format email tidak valid")
        @Size(max = 255, message = "Email maksimal 255 karakter")
        private String email;

        @Size(max = 50, message = "Nomor telepon maksimal 50 karakter")
        private String phone;

        private String address;

        private UUID user;

        @Size(max = 20, message = "NPWP maksimal 20 karakter")
        @Pattern(regexp = "^$|^[0-9.\\-]{15,20}$", message = "NPWP harus 15-20 digit")
        private String npwp;

        @Size(max = 16, message = "NIK KTP maksimal 16 karakter")
        @Pattern(regexp = "^$|^\\d{16}$", message = "NIK KTP harus 16 digit angka")
        private String nikKtp;

        @NotNull(message = "Status PTKP wajib diisi")
        private PtkpStatus ptkpStatus;

        @NotNull(message = "Tanggal mulai kerja wajib diisi")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate hireDate;

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate resignDate;

        @NotNull(message = "Tipe kepegawaian wajib diisi")
        private EmploymentType employmentType;

        @NotNull(message = "Status kepegawaian wajib diisi")
        private EmploymentStatus employmentStatus;

        @Size(max = 100, message = "Jabatan maksimal 100 karakter")
        private String jobTitle;

        @Size(max = 100, message = "Departemen maksimal 100 karakter")
        private String department;

        @Size(max = 100, message = "Nama bank maksimal 100 karakter")
        private String bankName;

        @Size(max = 50, message = "Nomor rekening maksimal 50 karakter")
        private String bankAccountNumber;

        @Size(max = 255, message = "Nama pemilik rekening maksimal 255 karakter")
        private String bankAccountName;

        @Size(max = 20, message = "Nomor BPJS Kesehatan maksimal 20 karakter")
        private String bpjsKesehatanNumber;

        @Size(max = 20, message = "Nomor BPJS Ketenagakerjaan maksimal 20 karakter")
        private String bpjsKetenagakerjaanNumber;

        private String notes;

        private Boolean active;
    }

    private Employee toEntity(EmployeeForm form) {
        Employee entity = new Employee();
        BeanUtils.copyProperties(form, entity, "id", "user");
        if (form.getUser() != null) {
            User u = userRepository.findById(form.getUser())
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + form.getUser()));
            entity.setUser(u);
        }
        return entity;
    }

    private EmployeeForm toForm(Employee entity) {
        EmployeeForm form = new EmployeeForm();
        BeanUtils.copyProperties(entity, form, "user");
        if (entity.getUser() != null) {
            form.setUser(entity.getUser().getId());
        }
        return form;
    }

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
        EmployeeForm form = new EmployeeForm();
        form.setEmploymentStatus(EmploymentStatus.ACTIVE);
        form.setEmploymentType(EmploymentType.PERMANENT);
        form.setPtkpStatus(PtkpStatus.TK_0);

        model.addAttribute(ATTR_EMPLOYEE, form);
        addFormAttributes(model);
        return VIEW_FORM;
    }

    @PostMapping("/new")
    @PreAuthorize("hasAuthority('" + Permission.EMPLOYEE_CREATE + "')")
    public String create(
            @Valid @ModelAttribute("employee") EmployeeForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            addFormAttributes(model);
            return VIEW_FORM;
        }

        try {
            Employee employee = toEntity(form);
            Employee saved = employeeService.create(employee);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Karyawan berhasil ditambahkan");
            return REDIRECT_EMPLOYEES + "/" + saved.getEmployeeId();
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("NIK")) {
                bindingResult.rejectValue("employeeId", ERR_DUPLICATE, e.getMessage());
            } else if (e.getMessage().contains("NPWP")) {
                bindingResult.rejectValue("npwp", ERR_DUPLICATE, e.getMessage());
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
        model.addAttribute(ATTR_EMPLOYEE, toForm(employee));
        model.addAttribute(ATTR_EDIT_EMPLOYEE_ID, employeeId);
        addFormAttributes(model);
        return VIEW_FORM;
    }

    @PostMapping("/{employeeId}")
    @PreAuthorize("hasAuthority('" + Permission.EMPLOYEE_EDIT + "')")
    public String update(
            @PathVariable String employeeId,
            @Valid @ModelAttribute("employee") EmployeeForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            Employee existing = employeeService.findByEmployeeId(employeeId);
            form.setId(existing.getId());
            model.addAttribute(ATTR_EDIT_EMPLOYEE_ID, employeeId);
            addFormAttributes(model);
            return VIEW_FORM;
        }

        try {
            Employee existing = employeeService.findByEmployeeId(employeeId);
            Employee employee = toEntity(form);
            employeeService.updateWithUserLink(existing.getId(), employee);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Karyawan berhasil diperbarui");
            return REDIRECT_EMPLOYEES + "/" + form.getEmployeeId();
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("NIK")) {
                bindingResult.rejectValue("employeeId", ERR_DUPLICATE, e.getMessage());
            } else if (e.getMessage().contains("NPWP")) {
                bindingResult.rejectValue("npwp", ERR_DUPLICATE, e.getMessage());
            } else {
                bindingResult.reject("error", e.getMessage());
            }
            Employee existing = employeeService.findByEmployeeId(employeeId);
            form.setId(existing.getId());
            model.addAttribute(ATTR_EDIT_EMPLOYEE_ID, employeeId);
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
