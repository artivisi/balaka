package com.artivisi.accountingfinance.controller.api;

import com.artivisi.accountingfinance.entity.Employee;
import com.artivisi.accountingfinance.entity.PayrollDetail;
import com.artivisi.accountingfinance.entity.PayrollRun;
import com.artivisi.accountingfinance.entity.PayrollStatus;
import com.artivisi.accountingfinance.entity.PtkpStatus;
import com.artivisi.accountingfinance.service.EmployeeService;
import com.artivisi.accountingfinance.service.PayrollService;
import com.artivisi.accountingfinance.service.PayrollService.YearlyPayrollSummary;
import com.artivisi.accountingfinance.service.Pph21CalculationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payroll")
@Tag(name = "Payroll", description = "Payroll run management, PPh 21 calculation, and 1721-A1 generation. "
        + "Lifecycle: DRAFT → CALCULATED → APPROVED → POSTED. "
        + "PPh 21 uses TER method (PMK 168/2023) for Jan–Nov, December annual reconciliation (PP 58/2023). "
        + "Posting creates a journal entry using the 'Post Gaji Bulanan' template (loaded via industry seed data). "
        + "Summary and 1721-A1 endpoints only return data from POSTED payroll runs.")
@PreAuthorize("hasAuthority('SCOPE_tax-export:read')")
@RequiredArgsConstructor
@Slf4j
public class PayrollApiController {

    private final PayrollService payrollService;
    private final EmployeeService employeeService;
    private final Pph21CalculationService pph21CalculationService;

    // ==================== PAYROLL RUN CRUD ====================

    @GetMapping
    @Operation(summary = "List payroll runs with optional filters")
    @ApiResponse(responseCode = "200", description = "Paginated list of payroll runs")
    public ResponseEntity<Page<PayrollRunResponse>> list(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) PayrollStatus status,
            Pageable pageable) {
        Page<PayrollRun> page;
        if (status != null) {
            page = payrollService.findByStatus(status, pageable);
        } else {
            page = payrollService.findAll(pageable);
        }

        // Filter by year if provided
        if (year != null) {
            String yearPrefix = String.valueOf(year);
            page = page.map(run -> run); // Keep pagination but filter below
            Page<PayrollRun> finalPage = page;
            List<PayrollRunResponse> filtered = finalPage.getContent().stream()
                    .filter(r -> r.getPayrollPeriod().startsWith(yearPrefix))
                    .map(PayrollRunResponse::from)
                    .toList();
            return ResponseEntity.ok(new org.springframework.data.domain.PageImpl<>(
                    filtered, pageable, filtered.size()));
        }

        return ResponseEntity.ok(page.map(PayrollRunResponse::from));
    }

    @PostMapping
    @Operation(summary = "Create a new payroll run",
            description = "Creates a DRAFT payroll run for the given period. Only one run per period allowed.")
    @ApiResponse(responseCode = "201", description = "Payroll run created in DRAFT status")
    @ApiResponse(responseCode = "400", description = "Payroll for this period already exists")
    public ResponseEntity<PayrollRunResponse> create(
            @Valid @RequestBody PayrollRunRequest request) {
        log.info("API: Create payroll run - period={}", request.payrollPeriod());

        YearMonth period = YearMonth.parse(request.payrollPeriod());
        PayrollRun run = payrollService.createPayrollRun(period);

        if (request.notes() != null) {
            run.setNotes(request.notes());
        }

        log.info("API: Payroll run created - id={}, period={}", run.getId(), run.getPayrollPeriod());
        return ResponseEntity.status(HttpStatus.CREATED).body(PayrollRunResponse.from(run));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payroll run detail with all details")
    @ApiResponse(responseCode = "200", description = "Payroll run with details")
    @ApiResponse(responseCode = "404", description = "Payroll run not found")
    public ResponseEntity<PayrollRunDetailResponse> get(@PathVariable UUID id) {
        return payrollService.findById(id)
                .map(run -> {
                    List<PayrollDetail> details = payrollService.getPayrollDetails(id);
                    return ResponseEntity.ok(PayrollRunDetailResponse.from(run, details));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/calculate")
    @Operation(summary = "Calculate PPh 21 for all employees in this run",
            description = "Requires DRAFT or CALCULATED status. Calculates BPJS and PPh 21 for all active employees. "
                    + "PPh 21 uses TER method (PMK 168/2023) for Jan–Nov: PPh 21 = gross × TER rate. "
                    + "December uses annual reconciliation with progressive brackets (PP 58/2023). "
                    + "Transitions status to CALCULATED.")
    @ApiResponse(responseCode = "200", description = "Payroll calculated, status set to CALCULATED")
    @ApiResponse(responseCode = "400", description = "No active employees or invalid status for calculation")
    public ResponseEntity<PayrollRunResponse> calculate(
            @PathVariable UUID id,
            @Valid @RequestBody PayrollCalculateRequest request) {
        log.info("API: Calculate payroll - id={}, baseSalary={}", id, request.baseSalary());

        int jkkRiskClass = request.jkkRiskClass() != null ? request.jkkRiskClass() : 1;
        PayrollRun run = payrollService.calculatePayroll(id, request.baseSalary(), jkkRiskClass);

        log.info("API: Payroll calculated - id={}, employeeCount={}", run.getId(), run.getEmployeeCount());
        return ResponseEntity.ok(PayrollRunResponse.from(run));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve payroll run",
            description = "Requires CALCULATED status. Transitions to APPROVED, enabling posting.")
    @ApiResponse(responseCode = "200", description = "Payroll approved, status set to APPROVED")
    @ApiResponse(responseCode = "400", description = "Payroll not in CALCULATED status")
    public ResponseEntity<PayrollRunResponse> approve(@PathVariable UUID id) {
        log.info("API: Approve payroll - id={}", id);

        PayrollRun run = payrollService.approvePayroll(id);

        log.info("API: Payroll approved - id={}", run.getId());
        return ResponseEntity.ok(PayrollRunResponse.from(run));
    }

    @PostMapping("/{id}/post")
    @Operation(summary = "Post payroll to accounting (create journal entry)",
            description = "Requires APPROVED status. Creates a journal entry using the payroll template "
                    + "(configured via app.payroll.template-id). "
                    + "Template lines with zero amounts are skipped (e.g., PPh 21 = 0 for low-salary UMKM employees). "
                    + "Transitions to POSTED. Only POSTED payroll appears in summary and 1721-A1 endpoints. "
                    + "To verify journal entries after posting, use GET /api/analysis/transactions/{transactionId}.")
    @ApiResponse(responseCode = "200", description = "Payroll posted, journal entry created. "
            + "Response includes transactionId — query via GET /api/analysis/transactions/{transactionId} for journal entries")
    @ApiResponse(responseCode = "400", description = "Payroll not in APPROVED status")
    @ApiResponse(responseCode = "500", description = "Payroll template not found — configure app.payroll.template-id")
    public ResponseEntity<PayrollRunResponse> post(@PathVariable UUID id) {
        log.info("API: Post payroll - id={}", id);

        PayrollRun run = payrollService.postPayroll(id);

        log.info("API: Payroll posted - id={}, transactionId={}", run.getId(),
                run.getTransaction() != null ? run.getTransaction().getId() : "null");
        return ResponseEntity.ok(PayrollRunResponse.from(run));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a draft payroll run",
            description = "Only DRAFT status can be deleted.")
    @ApiResponse(responseCode = "204", description = "Payroll run deleted")
    @ApiResponse(responseCode = "400", description = "Payroll not in DRAFT status")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        log.info("API: Delete payroll - id={}", id);

        payrollService.delete(id);

        log.info("API: Payroll deleted - id={}", id);
        return ResponseEntity.noContent().build();
    }

    // ==================== 1721-A1 ====================

    @GetMapping("/employees/{employeeId}/1721-a1")
    @Operation(summary = "Generate 1721-A1 data for an employee",
            description = "Annual tax proof (bukti potong). Only includes POSTED payroll runs. "
                    + "Monthly breakdown shows TER-based withholdings (Jan–Nov) and December reconciliation. "
                    + "Annual PPh 21 terutang uses progressive brackets on PKP. "
                    + "Returns 404 if no POSTED payroll exists for this employee in the given year.")
    @ApiResponse(responseCode = "200", description = "1721-A1 data")
    @ApiResponse(responseCode = "404", description = "Employee not found or no POSTED payroll data for the year")
    public ResponseEntity<A1Response> get1721A1(
            @PathVariable UUID employeeId,
            @RequestParam int year) {
        Employee employee = employeeService.findById(employeeId);

        List<PayrollDetail> yearlyDetails = payrollService.getYearlyPayrollDetails(employeeId, year);
        if (yearlyDetails.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        YearlyPayrollSummary summary = payrollService.getYearlyPayrollSummary(employeeId, year);

        // Calculate 1721-A1 using annual reconciliation method
        BigDecimal penghasilanBruto = summary.totalGross();
        BigDecimal biayaJabatan = penghasilanBruto.multiply(new BigDecimal("5"))
                .divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP)
                .min(Pph21CalculationService.BIAYA_JABATAN_ANNUAL_MAX);
        BigDecimal penghasilanNeto = penghasilanBruto.subtract(biayaJabatan);
        BigDecimal ptkp = employee.getPtkpStatus().getAnnualAmount();
        BigDecimal pkpRaw = penghasilanNeto.subtract(ptkp).max(BigDecimal.ZERO);
        // Round down to nearest 1000
        BigDecimal pkp = pkpRaw.divide(new BigDecimal("1000"), 0, RoundingMode.FLOOR)
                .multiply(new BigDecimal("1000"));
        BigDecimal pph21Terutang = pph21CalculationService.calculateProgressiveTax(pkp);
        BigDecimal pph21Dipotong = summary.totalPph21();
        BigDecimal pph21KurangBayar = pph21Terutang.subtract(pph21Dipotong);

        List<A1MonthlyBreakdown> monthlyBreakdown = yearlyDetails.stream()
                .map(d -> new A1MonthlyBreakdown(
                        d.getPayrollRun().getPayrollPeriod(),
                        d.getGrossSalary(),
                        d.getPph21()))
                .toList();

        A1Response response = new A1Response(
                year,
                new A1Employee(
                        employee.getName(),
                        employee.getNpwp(),
                        employee.getNikKtp(),
                        employee.getPtkpStatus(),
                        employee.getHireDate(),
                        employee.getResignDate(),
                        summary.monthCount()
                ),
                new A1Calculation(
                        penghasilanBruto,
                        biayaJabatan,
                        penghasilanNeto,
                        ptkp,
                        pkp,
                        pph21Terutang,
                        pph21Dipotong,
                        pph21KurangBayar
                ),
                monthlyBreakdown
        );

        return ResponseEntity.ok(response);
    }

    // ==================== PPh 21 SUMMARY ====================

    @GetMapping("/pph21/summary")
    @Operation(summary = "Annual PPh 21 summary across all employees",
            description = "Aggregates PPh 21 data from POSTED payroll runs only. "
                    + "Returns empty employees list if no payroll has been posted for the year.")
    @ApiResponse(responseCode = "200", description = "PPh 21 summary (empty if no POSTED runs)")
    public ResponseEntity<Pph21SummaryResponse> pph21Summary(@RequestParam int year) {
        List<UUID> employeeIds = payrollService.getEmployeesWithPayrollInYear(year);

        List<Pph21EmployeeSummary> employees = new ArrayList<>();
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalPph21 = BigDecimal.ZERO;

        for (UUID employeeId : employeeIds) {
            YearlyPayrollSummary summary = payrollService.getYearlyPayrollSummary(employeeId, year);
            employees.add(new Pph21EmployeeSummary(
                    summary.employee().getId(),
                    summary.employee().getEmployeeId(),
                    summary.employee().getName(),
                    summary.employee().getNpwp(),
                    summary.monthCount(),
                    summary.totalGross(),
                    summary.totalPph21()
            ));
            totalGross = totalGross.add(summary.totalGross());
            totalPph21 = totalPph21.add(summary.totalPph21());
        }

        return ResponseEntity.ok(new Pph21SummaryResponse(year, employees, totalGross, totalPph21));
    }

    // ==================== DTOs ====================

    public record PayrollRunRequest(
            @NotBlank(message = "Periode wajib diisi (format: YYYY-MM)")
            String payrollPeriod,

            String notes
    ) {}

    public record PayrollCalculateRequest(
            @NotNull(message = "Gaji pokok wajib diisi")
            BigDecimal baseSalary,

            Integer jkkRiskClass
    ) {}

    public record PayrollRunResponse(
            UUID id,
            String payrollPeriod,
            LocalDate periodStart,
            LocalDate periodEnd,
            PayrollStatus status,
            BigDecimal totalGross,
            BigDecimal totalDeductions,
            BigDecimal totalNetPay,
            BigDecimal totalCompanyBpjs,
            BigDecimal totalPph21,
            int employeeCount,
            String notes,
            UUID transactionId,
            LocalDateTime postedAt
    ) {
        public static PayrollRunResponse from(PayrollRun run) {
            return new PayrollRunResponse(
                    run.getId(),
                    run.getPayrollPeriod(),
                    run.getPeriodStart(),
                    run.getPeriodEnd(),
                    run.getStatus(),
                    run.getTotalGross(),
                    run.getTotalDeductions(),
                    run.getTotalNetPay(),
                    run.getTotalCompanyBpjs(),
                    run.getTotalPph21(),
                    run.getEmployeeCount(),
                    run.getNotes(),
                    run.getTransaction() != null ? run.getTransaction().getId() : null,
                    run.getPostedAt()
            );
        }
    }

    public record PayrollRunDetailResponse(
            UUID id,
            String payrollPeriod,
            PayrollStatus status,
            BigDecimal totalGross,
            BigDecimal totalPph21,
            int employeeCount,
            List<PayrollDetailResponse> details
    ) {
        public static PayrollRunDetailResponse from(PayrollRun run, List<PayrollDetail> details) {
            return new PayrollRunDetailResponse(
                    run.getId(),
                    run.getPayrollPeriod(),
                    run.getStatus(),
                    run.getTotalGross(),
                    run.getTotalPph21(),
                    run.getEmployeeCount(),
                    details.stream().map(PayrollDetailResponse::from).toList()
            );
        }
    }

    public record PayrollDetailResponse(
            UUID id,
            UUID employeeId,
            String employeeName,
            BigDecimal baseSalary,
            BigDecimal grossSalary,
            BigDecimal pph21,
            BigDecimal totalDeductions,
            BigDecimal netPay,
            BigDecimal bpjsKesCompany,
            BigDecimal bpjsKesEmployee,
            BigDecimal bpjsJkk,
            BigDecimal bpjsJkm,
            BigDecimal bpjsJhtCompany,
            BigDecimal bpjsJhtEmployee,
            BigDecimal bpjsJpCompany,
            BigDecimal bpjsJpEmployee,
            int jkkRiskClass
    ) {
        public static PayrollDetailResponse from(PayrollDetail d) {
            return new PayrollDetailResponse(
                    d.getId(),
                    d.getEmployee().getId(),
                    d.getEmployee().getName(),
                    d.getBaseSalary(),
                    d.getGrossSalary(),
                    d.getPph21(),
                    d.getTotalDeductions(),
                    d.getNetPay(),
                    d.getBpjsKesCompany(),
                    d.getBpjsKesEmployee(),
                    d.getBpjsJkk(),
                    d.getBpjsJkm(),
                    d.getBpjsJhtCompany(),
                    d.getBpjsJhtEmployee(),
                    d.getBpjsJpCompany(),
                    d.getBpjsJpEmployee(),
                    d.getJkkRiskClass()
            );
        }
    }

    // 1721-A1 DTOs
    public record A1Response(
            int taxYear,
            A1Employee employee,
            A1Calculation calculation,
            List<A1MonthlyBreakdown> monthlyBreakdown
    ) {}

    public record A1Employee(
            String name,
            String npwp,
            String nikKtp,
            PtkpStatus ptkpStatus,
            LocalDate hireDate,
            LocalDate resignDate,
            int masaKerja
    ) {}

    public record A1Calculation(
            BigDecimal penghasilanBruto,
            BigDecimal biayaJabatan,
            BigDecimal penghasilanNeto,
            BigDecimal ptkp,
            BigDecimal pkp,
            BigDecimal pph21Terutang,
            BigDecimal pph21Dipotong,
            BigDecimal pph21KurangBayar
    ) {}

    public record A1MonthlyBreakdown(
            String period,
            BigDecimal grossSalary,
            BigDecimal pph21
    ) {}

    // PPh 21 Summary DTOs
    public record Pph21SummaryResponse(
            int taxYear,
            List<Pph21EmployeeSummary> employees,
            BigDecimal totalGross,
            BigDecimal totalPph21
    ) {}

    public record Pph21EmployeeSummary(
            UUID id,
            String employeeId,
            String name,
            String npwp,
            int monthCount,
            BigDecimal totalGross,
            BigDecimal totalPph21
    ) {}
}
