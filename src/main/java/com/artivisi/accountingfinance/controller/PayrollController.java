package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.PayrollDetail;
import com.artivisi.accountingfinance.entity.PayrollRun;
import com.artivisi.accountingfinance.entity.PayrollStatus;
import com.artivisi.accountingfinance.service.PayrollReportService;
import com.artivisi.accountingfinance.service.PayrollService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/payroll")
public class PayrollController {

    private static final String ATTR_CURRENT_PAGE = "currentPage";
    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";
    private static final String ATTR_ERROR_MESSAGE = "errorMessage";
    private static final String PAYROLL_NOT_FOUND = "Payroll tidak ditemukan";
    private static final String CONTENT_TYPE_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String ATTACHMENT_FILENAME = "attachment; filename=\"";

    private final PayrollService payrollService;
    private final PayrollReportService payrollReportService;

    public PayrollController(PayrollService payrollService, PayrollReportService payrollReportService) {
        this.payrollService = payrollService;
        this.payrollReportService = payrollReportService;
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
        model.addAttribute(ATTR_CURRENT_PAGE, "payroll");

        return "payroll/list";
    }

    @GetMapping("/new")
    public String newPayrollForm(Model model) {
        model.addAttribute("payrollForm", new PayrollForm());
        model.addAttribute(ATTR_CURRENT_PAGE, "payroll");
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
            model.addAttribute(ATTR_CURRENT_PAGE, "payroll");
            return "payroll/form";
        }

        try {
            YearMonth period = YearMonth.parse(form.getPeriod());

            if (payrollService.existsByPeriod(period.toString())) {
                bindingResult.rejectValue("period", "duplicate", "Payroll untuk periode ini sudah ada");
                model.addAttribute("riskClasses", getRiskClasses());
                model.addAttribute(ATTR_CURRENT_PAGE, "payroll");
                return "payroll/form";
            }

            // Create and calculate payroll
            PayrollRun payrollRun = payrollService.createPayrollRun(period);
            payrollRun = payrollService.calculatePayroll(
                payrollRun.getId(),
                form.getBaseSalary(),
                form.getJkkRiskClass()
            );

            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE,
                "Payroll untuk periode " + period.toString() + " berhasil dibuat dan dikalkulasi");

            return "redirect:/payroll/" + payrollRun.getId();

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
            return "redirect:/payroll/new";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        PayrollRun payrollRun = payrollService.findById(id)
            .orElseThrow(() -> new IllegalArgumentException(PAYROLL_NOT_FOUND));

        var details = payrollService.getPayrollDetails(id);

        model.addAttribute("payrollRun", payrollRun);
        model.addAttribute("details", details);
        model.addAttribute(ATTR_CURRENT_PAGE, "payroll");

        return "payroll/detail";
    }

    @PostMapping("/{id}/approve")
    public String approve(
            @PathVariable UUID id,
            RedirectAttributes redirectAttributes
    ) {
        try {
            PayrollRun payrollRun = payrollService.approvePayroll(id);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE,
                "Payroll periode " + payrollRun.getPayrollPeriod() + " berhasil di-approve");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
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
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE,
                "Payroll periode " + payrollRun.getPayrollPeriod() + " berhasil dibatalkan");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
        }

        return "redirect:/payroll/" + id;
    }

    @PostMapping("/{id}/post")
    public String post(
            @PathVariable UUID id,
            RedirectAttributes redirectAttributes
    ) {
        try {
            PayrollRun payrollRun = payrollService.postPayroll(id);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE,
                "Payroll periode " + payrollRun.getPayrollPeriod() + " berhasil di-posting ke jurnal");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
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
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE,
                "Payroll berhasil dikalkulasi ulang");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
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
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Payroll berhasil dihapus");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
        }

        return "redirect:/payroll";
    }

    // ==================== REPORT ENDPOINTS ====================

    @GetMapping("/{id}/export/summary/pdf")
    public ResponseEntity<byte[]> exportSummaryPdf(@PathVariable UUID id) {
        PayrollRun payrollRun = payrollService.findById(id)
            .orElseThrow(() -> new IllegalArgumentException(PAYROLL_NOT_FOUND));
        List<PayrollDetail> details = payrollService.getPayrollDetails(id);

        byte[] pdf = payrollReportService.exportPayrollSummaryToPdf(payrollRun, details);
        String filename = "rekap-gaji-" + payrollRun.getPayrollPeriod() + ".pdf";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME + filename + "\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    }

    @GetMapping("/{id}/export/summary/excel")
    public ResponseEntity<byte[]> exportSummaryExcel(@PathVariable UUID id) {
        PayrollRun payrollRun = payrollService.findById(id)
            .orElseThrow(() -> new IllegalArgumentException(PAYROLL_NOT_FOUND));
        List<PayrollDetail> details = payrollService.getPayrollDetails(id);

        byte[] excel = payrollReportService.exportPayrollSummaryToExcel(payrollRun, details);
        String filename = "rekap-gaji-" + payrollRun.getPayrollPeriod() + ".xlsx";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME + filename + "\"")
            .contentType(MediaType.parseMediaType(CONTENT_TYPE_XLSX))
            .body(excel);
    }

    @GetMapping("/{id}/export/pph21/pdf")
    public ResponseEntity<byte[]> exportPph21Pdf(@PathVariable UUID id) {
        PayrollRun payrollRun = payrollService.findById(id)
            .orElseThrow(() -> new IllegalArgumentException(PAYROLL_NOT_FOUND));
        List<PayrollDetail> details = payrollService.getPayrollDetails(id);

        byte[] pdf = payrollReportService.exportPph21ReportToPdf(payrollRun, details);
        String filename = "pph21-" + payrollRun.getPayrollPeriod() + ".pdf";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME + filename + "\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    }

    @GetMapping("/{id}/export/pph21/excel")
    public ResponseEntity<byte[]> exportPph21Excel(@PathVariable UUID id) {
        PayrollRun payrollRun = payrollService.findById(id)
            .orElseThrow(() -> new IllegalArgumentException(PAYROLL_NOT_FOUND));
        List<PayrollDetail> details = payrollService.getPayrollDetails(id);

        byte[] excel = payrollReportService.exportPph21ReportToExcel(payrollRun, details);
        String filename = "pph21-" + payrollRun.getPayrollPeriod() + ".xlsx";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME + filename + "\"")
            .contentType(MediaType.parseMediaType(CONTENT_TYPE_XLSX))
            .body(excel);
    }

    @GetMapping("/{id}/export/bpjs/pdf")
    public ResponseEntity<byte[]> exportBpjsPdf(@PathVariable UUID id) {
        PayrollRun payrollRun = payrollService.findById(id)
            .orElseThrow(() -> new IllegalArgumentException(PAYROLL_NOT_FOUND));
        List<PayrollDetail> details = payrollService.getPayrollDetails(id);

        byte[] pdf = payrollReportService.exportBpjsReportToPdf(payrollRun, details);
        String filename = "bpjs-" + payrollRun.getPayrollPeriod() + ".pdf";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME + filename + "\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    }

    @GetMapping("/{id}/export/bpjs/excel")
    public ResponseEntity<byte[]> exportBpjsExcel(@PathVariable UUID id) {
        PayrollRun payrollRun = payrollService.findById(id)
            .orElseThrow(() -> new IllegalArgumentException(PAYROLL_NOT_FOUND));
        List<PayrollDetail> details = payrollService.getPayrollDetails(id);

        byte[] excel = payrollReportService.exportBpjsReportToExcel(payrollRun, details);
        String filename = "bpjs-" + payrollRun.getPayrollPeriod() + ".xlsx";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME + filename + "\"")
            .contentType(MediaType.parseMediaType(CONTENT_TYPE_XLSX))
            .body(excel);
    }

    @GetMapping("/{id}/payslip/{employeeId}/pdf")
    public ResponseEntity<byte[]> exportPayslipPdf(@PathVariable UUID id, @PathVariable UUID employeeId) {
        PayrollRun payrollRun = payrollService.findById(id)
            .orElseThrow(() -> new IllegalArgumentException(PAYROLL_NOT_FOUND));
        List<PayrollDetail> details = payrollService.getPayrollDetails(id);

        PayrollDetail detail = details.stream()
            .filter(d -> d.getEmployee().getId().equals(employeeId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Data karyawan tidak ditemukan dalam payroll ini"));

        byte[] pdf = payrollReportService.generatePayslipPdf(payrollRun, detail);
        String filename = "slip-gaji-" + detail.getEmployeeId() + "-" + payrollRun.getPayrollPeriod() + ".pdf";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME + filename + "\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    }

    // ==================== BUKTI POTONG 1721-A1 ====================

    @GetMapping("/bukti-potong")
    public String buktiPotongPage(
            @RequestParam(required = false) Integer year,
            Model model
    ) {
        int selectedYear = year != null ? year : java.time.Year.now().getValue();
        List<UUID> employeeIds = payrollService.getEmployeesWithPayrollInYear(selectedYear);

        List<PayrollService.YearlyPayrollSummary> summaries = employeeIds.stream()
            .map(empId -> {
                try {
                    return payrollService.getYearlyPayrollSummary(empId, selectedYear);
                } catch (IllegalArgumentException e) {
                    return null;
                }
            })
            .filter(s -> s != null)
            .toList();

        // Generate year options (current year and 2 previous years)
        int currentYear = java.time.Year.now().getValue();
        List<Integer> yearOptions = java.util.List.of(currentYear, currentYear - 1, currentYear - 2);

        model.addAttribute("summaries", summaries);
        model.addAttribute("selectedYear", selectedYear);
        model.addAttribute("yearOptions", yearOptions);
        model.addAttribute(ATTR_CURRENT_PAGE, "payroll");

        return "payroll/bukti-potong";
    }

    @GetMapping("/bukti-potong/{employeeId}/{year}/pdf")
    public ResponseEntity<byte[]> exportBuktiPotongPdf(
            @PathVariable UUID employeeId,
            @PathVariable int year
    ) {
        var summary = payrollService.getYearlyPayrollSummary(employeeId, year);
        byte[] pdf = payrollReportService.generateBuktiPotong1721A1(summary);
        String filename = "1721-A1-" + summary.employee().getEmployeeId() + "-" + year + ".pdf";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ATTACHMENT_FILENAME + filename + "\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
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
