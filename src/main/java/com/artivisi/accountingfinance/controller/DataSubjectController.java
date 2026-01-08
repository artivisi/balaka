package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.Employee;
import com.artivisi.accountingfinance.repository.EmployeeRepository;
import com.artivisi.accountingfinance.security.LogSanitizer;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.DataSubjectService;
import com.artivisi.accountingfinance.service.DataSubjectService.DataRetentionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
import java.util.UUID;

import static com.artivisi.accountingfinance.controller.ViewConstants.*;

/**
 * Controller for GDPR/UU PDP Data Subject Rights management.
 * Admin-only access for handling data subject requests.
 */
@Controller
@RequestMapping("/settings/data-subjects")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAuthority('" + Permission.DATA_SUBJECT_VIEW + "')")
public class DataSubjectController {

    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";
    private static final String ATTR_ERROR_MESSAGE = "errorMessage";
    private static final String ATTR_EMPLOYEE = "employee";
    private static final String ERR_EMPLOYEE_NOT_FOUND = "Employee not found: ";

    private final DataSubjectService dataSubjectService;
    private final EmployeeRepository employeeRepository;

    /**
     * List all employees for data subject management.
     */
    @GetMapping
    public String list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            Model model) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("employeeId"));
        Page<Employee> employees;

        if (search != null && !search.trim().isEmpty()) {
            employees = employeeRepository.findByFiltersAndSearch(search.trim(), null, null, pageRequest);
        } else {
            employees = employeeRepository.findByFilters(null, null, pageRequest);
        }

        model.addAttribute("employees", employees);
        model.addAttribute("search", search);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_SETTINGS);

        if ("true".equals(hxRequest)) {
            return "settings/data-subjects/fragments/employee-table :: table";
        }

        return "settings/data-subjects/list";
    }

    /**
     * View data subject detail including personal data and retention status.
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(ERR_EMPLOYEE_NOT_FOUND + id));

        DataRetentionStatus retentionStatus = dataSubjectService.getRetentionStatus(id);

        model.addAttribute(ATTR_EMPLOYEE, employee);
        model.addAttribute("retentionStatus", retentionStatus);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_SETTINGS);

        return "settings/data-subjects/detail";
    }

    /**
     * Export personal data for a data subject (DSAR - Data Subject Access Request).
     */
    @GetMapping("/{id}/export")
    @PreAuthorize("hasAuthority('" + Permission.DATA_SUBJECT_EXPORT + "')")
    public String exportData(@PathVariable UUID id, Model model) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(ERR_EMPLOYEE_NOT_FOUND + id));

        Map<String, Object> exportedData = dataSubjectService.exportPersonalData(id);

        model.addAttribute(ATTR_EMPLOYEE, employee);
        model.addAttribute("exportedData", exportedData);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_SETTINGS);

        return "settings/data-subjects/export";
    }

    /**
     * Show anonymization confirmation page.
     */
    @GetMapping("/{id}/anonymize")
    @PreAuthorize("hasAuthority('" + Permission.DATA_SUBJECT_ANONYMIZE + "')")
    public String showAnonymizeForm(@PathVariable UUID id, Model model) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(ERR_EMPLOYEE_NOT_FOUND + id));

        DataRetentionStatus retentionStatus = dataSubjectService.getRetentionStatus(id);

        model.addAttribute(ATTR_EMPLOYEE, employee);
        model.addAttribute("retentionStatus", retentionStatus);
        model.addAttribute(ATTR_CURRENT_PAGE, PAGE_SETTINGS);

        return "settings/data-subjects/anonymize";
    }

    /**
     * Process anonymization request (Right to Erasure).
     */
    @PostMapping("/{id}/anonymize")
    @PreAuthorize("hasAuthority('" + Permission.DATA_SUBJECT_ANONYMIZE + "')")
    public String anonymize(
            @PathVariable UUID id,
            @RequestParam String reason,
            RedirectAttributes redirectAttributes) {

        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(ERR_EMPLOYEE_NOT_FOUND + id));

        if (reason == null || reason.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, "Alasan anonimisasi wajib diisi");
            return "redirect:/settings/data-subjects/" + id + "/anonymize";
        }

        String originalName = employee.getName();
        dataSubjectService.anonymizeEmployee(id, reason.trim());

        log.info("Employee data anonymized via UI: {} ({})",
                LogSanitizer.sanitize(originalName), id);
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE,
                "Data karyawan " + originalName + " berhasil dianonimisasi sesuai permintaan hak hapus data (GDPR Art. 17)");

        return "redirect:/settings/data-subjects";
    }
}
