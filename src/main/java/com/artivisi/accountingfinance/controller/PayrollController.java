package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.PayrollRun;
import com.artivisi.accountingfinance.entity.PayrollStatus;
import com.artivisi.accountingfinance.service.PayrollService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.UUID;

@Controller
@RequestMapping("/payroll")
public class PayrollController {

    private final PayrollService payrollService;

    public PayrollController(PayrollService payrollService) {
        this.payrollService = payrollService;
    }

    @GetMapping
    public String list(
            @RequestParam(required = false) PayrollStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PayrollRun> payrollRuns = payrollService.findByStatus(status, pageable);

        model.addAttribute("payrollRuns", payrollRuns);
        model.addAttribute("statuses", Arrays.asList(PayrollStatus.values()));
        model.addAttribute("selectedStatus", status);
        model.addAttribute("currentPage", "payroll");

        return "payroll/list";
    }

    @GetMapping("/new")
    public String newPayrollForm(Model model) {
        model.addAttribute("payrollForm", new PayrollForm());
        model.addAttribute("currentPage", "payroll");
        model.addAttribute("riskClasses", getRiskClasses());

        // Suggest next period
        YearMonth suggestedPeriod = YearMonth.now();
        model.addAttribute("suggestedPeriod", suggestedPeriod.toString());

        return "payroll/form";
    }

    @PostMapping("/create")
    public String createPayroll(
            @Valid @ModelAttribute PayrollForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("riskClasses", getRiskClasses());
            model.addAttribute("currentPage", "payroll");
            return "payroll/form";
        }

        try {
            YearMonth period = YearMonth.parse(form.getPeriod());

            if (payrollService.existsByPeriod(period.toString())) {
                bindingResult.rejectValue("period", "duplicate", "Payroll untuk periode ini sudah ada");
                model.addAttribute("riskClasses", getRiskClasses());
                model.addAttribute("currentPage", "payroll");
                return "payroll/form";
            }

            // Create and calculate payroll
            PayrollRun payrollRun = payrollService.createPayrollRun(period);
            payrollRun = payrollService.calculatePayroll(
                payrollRun.getId(),
                form.getBaseSalary(),
                form.getJkkRiskClass()
            );

            redirectAttributes.addFlashAttribute("successMessage",
                "Payroll untuk periode " + period.toString() + " berhasil dibuat dan dikalkulasi");

            return "redirect:/payroll/" + payrollRun.getId();

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/payroll/new";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        PayrollRun payrollRun = payrollService.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Payroll tidak ditemukan"));

        var details = payrollService.getPayrollDetails(id);

        model.addAttribute("payrollRun", payrollRun);
        model.addAttribute("details", details);
        model.addAttribute("currentPage", "payroll");

        return "payroll/detail";
    }

    @PostMapping("/{id}/approve")
    public String approve(
            @PathVariable UUID id,
            RedirectAttributes redirectAttributes
    ) {
        try {
            PayrollRun payrollRun = payrollService.approvePayroll(id);
            redirectAttributes.addFlashAttribute("successMessage",
                "Payroll periode " + payrollRun.getPayrollPeriod() + " berhasil di-approve");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/payroll/" + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancel(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason,
            RedirectAttributes redirectAttributes
    ) {
        try {
            PayrollRun payrollRun = payrollService.cancelPayroll(id, reason);
            redirectAttributes.addFlashAttribute("successMessage",
                "Payroll periode " + payrollRun.getPayrollPeriod() + " berhasil dibatalkan");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/payroll/" + id;
    }

    @PostMapping("/{id}/recalculate")
    public String recalculate(
            @PathVariable UUID id,
            @RequestParam BigDecimal baseSalary,
            @RequestParam(defaultValue = "1") int jkkRiskClass,
            RedirectAttributes redirectAttributes
    ) {
        try {
            PayrollRun payrollRun = payrollService.calculatePayroll(id, baseSalary, jkkRiskClass);
            redirectAttributes.addFlashAttribute("successMessage",
                "Payroll berhasil dikalkulasi ulang");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/payroll/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(
            @PathVariable UUID id,
            RedirectAttributes redirectAttributes
    ) {
        try {
            payrollService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "Payroll berhasil dihapus");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/payroll";
    }

    private java.util.List<RiskClassOption> getRiskClasses() {
        return java.util.List.of(
            new RiskClassOption(1, "Kelas 1 - Sangat Rendah (0.24%) - IT, Jasa"),
            new RiskClassOption(2, "Kelas 2 - Rendah (0.54%) - Retail, Perdagangan"),
            new RiskClassOption(3, "Kelas 3 - Sedang (0.89%) - Manufaktur Ringan"),
            new RiskClassOption(4, "Kelas 4 - Tinggi (1.27%) - Konstruksi"),
            new RiskClassOption(5, "Kelas 5 - Sangat Tinggi (1.74%) - Pertambangan")
        );
    }

    public record RiskClassOption(int value, String label) {}

    public static class PayrollForm {
        @NotNull(message = "Periode wajib diisi")
        private String period;

        @NotNull(message = "Gaji pokok wajib diisi")
        private BigDecimal baseSalary = new BigDecimal("10000000");

        private int jkkRiskClass = 1;

        public String getPeriod() {
            return period;
        }

        public void setPeriod(String period) {
            this.period = period;
        }

        public BigDecimal getBaseSalary() {
            return baseSalary;
        }

        public void setBaseSalary(BigDecimal baseSalary) {
            this.baseSalary = baseSalary;
        }

        public int getJkkRiskClass() {
            return jkkRiskClass;
        }

        public void setJkkRiskClass(int jkkRiskClass) {
            this.jkkRiskClass = jkkRiskClass;
        }
    }
}
