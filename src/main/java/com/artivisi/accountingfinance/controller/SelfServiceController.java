package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.Employee;
import com.artivisi.accountingfinance.entity.PayrollDetail;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.repository.EmployeeRepository;
import com.artivisi.accountingfinance.repository.PayrollDetailRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.PayrollReportService;
import com.artivisi.accountingfinance.service.PayrollService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/self-service")
@RequiredArgsConstructor
@Slf4j
public class SelfServiceController {

    private static final String ATTR_NO_EMPLOYEE = "noEmployee";
    private static final String ATTR_EMPLOYEE = "employee";
    private static final String REDIRECT_PROFILE = "redirect:/self-service/profile";

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final PayrollDetailRepository payrollDetailRepository;
    private final PayrollReportService payrollReportService;
    private final PayrollService payrollService;

    // ==================== MY PAYSLIPS ====================

    @GetMapping("/payslips")
    @PreAuthorize("hasAuthority('" + Permission.OWN_PAYSLIP_VIEW + "')")
    public String listPayslips(
            @RequestParam(required = false) Integer year,
            Model model) {

        Optional<Employee> employeeOpt = getCurrentEmployee();
        if (employeeOpt.isEmpty()) {
            model.addAttribute(ATTR_NO_EMPLOYEE, true);
            return "self-service/payslips";
        }

        Employee employee = employeeOpt.get();
        int selectedYear = year != null ? year : LocalDate.now().getYear();

        String yearPrefix = String.valueOf(selectedYear);
        List<PayrollDetail> payslips = payrollDetailRepository.findPostedByEmployeeIdAndYear(
                employee.getId(), yearPrefix);

        model.addAttribute(ATTR_EMPLOYEE, employee);
        model.addAttribute("payslips", payslips);
        model.addAttribute("selectedYear", selectedYear);
        model.addAttribute("years", getAvailableYears());

        return "self-service/payslips";
    }

    @GetMapping("/payslips/{id}/pdf")
    @PreAuthorize("hasAuthority('" + Permission.OWN_PAYSLIP_VIEW + "')")
    public ResponseEntity<byte[]> downloadPayslip(@PathVariable UUID id) {
        Optional<Employee> employeeOpt = getCurrentEmployee();
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Employee employee = employeeOpt.get();
        PayrollDetail detail = payrollDetailRepository.findById(id).orElse(null);

        if (detail == null || !detail.getEmployee().getId().equals(employee.getId())) {
            return ResponseEntity.notFound().build();
        }

        byte[] pdf = payrollReportService.generatePayslipPdf(detail.getPayrollRun(), detail);
        String filename = String.format("slip-gaji-%s-%s.pdf",
                detail.getPayrollRun().getPayrollPeriod(),
                employee.getEmployeeId());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // ==================== MY BUKTI POTONG ====================

    @GetMapping("/bukti-potong")
    @PreAuthorize("hasAuthority('" + Permission.OWN_PAYSLIP_VIEW + "')")
    public String listBuktiPotong(
            @RequestParam(required = false) Integer year,
            Model model) {

        Optional<Employee> employeeOpt = getCurrentEmployee();
        if (employeeOpt.isEmpty()) {
            model.addAttribute(ATTR_NO_EMPLOYEE, true);
            return "self-service/bukti-potong";
        }

        Employee employee = employeeOpt.get();
        int selectedYear = year != null ? year : LocalDate.now().getYear();

        // Check if employee has any posted payroll in this year
        String yearPrefix = String.valueOf(selectedYear);
        List<PayrollDetail> payslips = payrollDetailRepository.findPostedByEmployeeIdAndYear(
                employee.getId(), yearPrefix);

        model.addAttribute(ATTR_EMPLOYEE, employee);
        model.addAttribute("hasPayrollData", !payslips.isEmpty());
        model.addAttribute("selectedYear", selectedYear);
        model.addAttribute("years", getAvailableYears());

        return "self-service/bukti-potong";
    }

    @GetMapping("/bukti-potong/{year}/pdf")
    @PreAuthorize("hasAuthority('" + Permission.OWN_PAYSLIP_VIEW + "')")
    public ResponseEntity<byte[]> downloadBuktiPotong(@PathVariable int year) {
        Optional<Employee> employeeOpt = getCurrentEmployee();
        if (employeeOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Employee employee = employeeOpt.get();

        try {
            PayrollService.YearlyPayrollSummary summary = payrollService.getYearlyPayrollSummary(employee.getId(), year);
            byte[] pdf = payrollReportService.generateBuktiPotong1721A1(summary);
            String filename = String.format("bukti-potong-1721-A1-%d-%s.pdf", year, employee.getEmployeeId());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (IllegalArgumentException _) {
            return ResponseEntity.notFound().build();
        }
    }

    // ==================== MY PROFILE ====================

    @GetMapping("/profile")
    @PreAuthorize("hasAuthority('" + Permission.OWN_PROFILE_VIEW + "')")
    public String viewProfile(Model model) {
        Optional<Employee> employeeOpt = getCurrentEmployee();
        if (employeeOpt.isEmpty()) {
            model.addAttribute(ATTR_NO_EMPLOYEE, true);
            return "self-service/profile";
        }

        model.addAttribute(ATTR_EMPLOYEE, employeeOpt.get());
        return "self-service/profile";
    }

    @GetMapping("/profile/edit")
    @PreAuthorize("hasAuthority('" + Permission.OWN_PROFILE_EDIT + "')")
    public String editProfileForm(Model model) {
        Optional<Employee> employeeOpt = getCurrentEmployee();
        if (employeeOpt.isEmpty()) {
            return REDIRECT_PROFILE;
        }

        model.addAttribute(ATTR_EMPLOYEE, employeeOpt.get());
        return "self-service/profile-edit";
    }

    @PostMapping("/profile/edit")
    @PreAuthorize("hasAuthority('" + Permission.OWN_PROFILE_EDIT + "')")
    public String updateProfile(
            @RequestParam String phone,
            @RequestParam String address,
            @RequestParam String bankName,
            @RequestParam String bankAccountNumber,
            @RequestParam String bankAccountName,
            RedirectAttributes redirectAttributes) {

        Optional<Employee> employeeOpt = getCurrentEmployee();
        if (employeeOpt.isEmpty()) {
            return REDIRECT_PROFILE;
        }

        Employee employee = employeeOpt.get();

        // Only allow updating limited fields
        employee.setPhone(phone);
        employee.setAddress(address);
        employee.setBankName(bankName);
        employee.setBankAccountNumber(bankAccountNumber);
        employee.setBankAccountName(bankAccountName);

        employeeRepository.save(employee);

        redirectAttributes.addFlashAttribute("success", "Profil berhasil diperbarui");
        return REDIRECT_PROFILE;
    }

    // ==================== HELPER METHODS ====================

    private Optional<Employee> getCurrentEmployee() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return Optional.empty();
        }

        Optional<User> userOpt = userRepository.findByUsername(auth.getName());
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }

        return employeeRepository.findByUserId(userOpt.get().getId());
    }

    private List<Integer> getAvailableYears() {
        int currentYear = LocalDate.now().getYear();
        return List.of(currentYear, currentYear - 1, currentYear - 2);
    }
}
