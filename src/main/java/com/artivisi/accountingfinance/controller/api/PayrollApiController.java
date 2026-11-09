package com.artivisi.accountingfinance.controller.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.artivisi.accountingfinance.entity.Employee;
import com.artivisi.accountingfinance.entity.PayrollAddition;
import com.artivisi.accountingfinance.entity.PayrollAdditionType;
import com.artivisi.accountingfinance.entity.PayrollDetail;
import com.artivisi.accountingfinance.entity.PayrollRun;
import com.artivisi.accountingfinance.entity.PayrollSchedule;
import com.artivisi.accountingfinance.entity.PayrollStatus;
import com.artivisi.accountingfinance.entity.PtkpStatus;
import com.artivisi.accountingfinance.service.EmployeeService;
import com.artivisi.accountingfinance.service.PayrollReconciliationService;
import com.artivisi.accountingfinance.service.PayrollService;
import com.artivisi.accountingfinance.service.PayrollService.YearlyPayrollSummary;
import com.artivisi.accountingfinance.service.Pph21CalculationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
@PreAuthorize("hasAuthority('SCOPE_tax-export:read') and hasAuthority('PAYROLL_VIEW')")
@RequiredArgsConstructor
@Slf4j
public class PayrollApiController {

    private final PayrollService payrollService;
    private final EmployeeService employeeService;
    private final Pph21CalculationService pph21CalculationService;
    private final PayrollReconciliationService payrollReconciliationService;

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
    @PreAuthorize("hasAuthority('SCOPE_payroll:write') and hasAuthority('PAYROLL_CREATE')")
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
        return ResponseEntity.status(HttpStatus.CREATED).body(withWarnings(run));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payroll run detail with all details")
    @ApiResponse(responseCode = "200", description = "Payroll run with details")
    @ApiResponse(responseCode = "404", description = "Payroll run not found")
    public ResponseEntity<PayrollRunDetailResponse> get(@PathVariable UUID id) {
        return payrollService.findById(id)
                .map(run -> {
                    List<PayrollDetail> details = payrollService.getPayrollDetails(id);
                    return ResponseEntity.ok(PayrollRunDetailResponse.from(run, details,
                            payrollService.getAdditions(id), payrollReconciliationService.warningsForRun(id)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/calculate")
    @PreAuthorize("hasAuthority('SCOPE_payroll:write') and hasAuthority('PAYROLL_CREATE')")
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
        return ResponseEntity.ok(withWarnings(run));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('SCOPE_payroll:write') and hasAuthority('PAYROLL_APPROVE')")
    @Operation(summary = "Approve payroll run",
            description = "Requires CALCULATED status. Transitions to APPROVED, enabling posting.")
    @ApiResponse(responseCode = "200", description = "Payroll approved, status set to APPROVED")
    @ApiResponse(responseCode = "400", description = "Payroll not in CALCULATED status")
    public ResponseEntity<PayrollRunResponse> approve(@PathVariable UUID id) {
        log.info("API: Approve payroll - id={}", id);

        PayrollRun run = payrollService.approvePayroll(id);

        log.info("API: Payroll approved - id={}", run.getId());
        return ResponseEntity.ok(withWarnings(run));
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('SCOPE_payroll:write') and hasAuthority('PAYROLL_POST')")
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
        return ResponseEntity.ok(withWarnings(run));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_payroll:write') and hasAuthority('PAYROLL_CREATE')")
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

    private PayrollRunResponse withWarnings(PayrollRun run) {
        return PayrollRunResponse.from(run, payrollReconciliationService.warningsForRun(run.getId()));
    }

    // ==================== BONUS AND THR ====================

    @GetMapping("/{id}/additions")
    @Operation(summary = "List bonus and THR rows of a payroll run")
    @ApiResponse(responseCode = "200", description = "Rows of the run")
    public ResponseEntity<List<PayrollAdditionResponse>> listAdditions(@PathVariable UUID id) {
        return ResponseEntity.ok(payrollService.getAdditions(id).stream().map(PayrollAdditionResponse::from).toList());
    }

    @PostMapping("/{id}/additions")
    @PreAuthorize("hasAuthority('SCOPE_payroll:write') and hasAuthority('PAYROLL_CREATE')")
    @Operation(summary = "Add a bonus or THR row for one employee",
            description = "Requires DRAFT or CALCULATED status. A CALCULATED run is recalculated at once with its "
                    + "base salary and JKK class. The amount enters the TER base of the month and the December "
                    + "reconciliation. BPJS is not affected.")
    @ApiResponse(responseCode = "201", description = "Row added")
    @ApiResponse(responseCode = "400", description = "Run status does not allow it, amount not positive, or employee not active")
    public ResponseEntity<PayrollAdditionResponse> addAddition(
            @PathVariable UUID id,
            @Valid @RequestBody PayrollAdditionRequest request) {
        PayrollAddition addition = payrollService.addAddition(id, request.employeeId(), request.type(),
                request.amount(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(PayrollAdditionResponse.from(addition));
    }

    @DeleteMapping("/{id}/additions/{additionId}")
    @PreAuthorize("hasAuthority('SCOPE_payroll:write') and hasAuthority('PAYROLL_CREATE')")
    @Operation(summary = "Remove a bonus or THR row",
            description = "Requires DRAFT or CALCULATED status. A CALCULATED run is recalculated at once.")
    @ApiResponse(responseCode = "204", description = "Row removed")
    @ApiResponse(responseCode = "400", description = "Run status does not allow it, or row not on this run")
    public ResponseEntity<Void> removeAddition(@PathVariable UUID id, @PathVariable UUID additionId) {
        payrollService.removeAddition(id, additionId);
        return ResponseEntity.noContent().build();
    }

    // ==================== RECONCILIATION ====================

    @GetMapping("/reconciliation")
    @Operation(summary = "Salary expense against payroll for twelve periods of a year",
            description = "Ledger figures use POSTED journals on the payroll template's salary accounts, closing "
                    + "entries excluded. Salary accounts are the debit lines with formula grossSalary, bonus or thr.")
    @ApiResponse(responseCode = "200", description = "One row per month with yearly totals")
    public ResponseEntity<PayrollReconciliationService.YearReconciliation> reconciliation(@RequestParam int year) {
        return ResponseEntity.ok(payrollReconciliationService.reconcileYear(year));
    }

    @GetMapping("/reconciliation/{period}")
    @Operation(summary = "Salary expense against payroll for one period (YYYY-MM)")
    @ApiResponse(responseCode = "200", description = "Reconciliation of the period")
    @ApiResponse(responseCode = "400", description = "Period is not YYYY-MM")
    public ResponseEntity<PayrollReconciliationService.PeriodReconciliation> reconciliationPeriod(@PathVariable YearMonth period) {
        return ResponseEntity.ok(payrollReconciliationService.reconcile(period));
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

        var annual = pph21CalculationService.calculateAnnualTax(
                summary.getTotalBruto(), summary.totalPensionContributions(), employee.getPtkpStatus());
        BigDecimal penghasilanBruto = annual.annualGross();
        BigDecimal biayaJabatan = annual.biayaJabatan();
        BigDecimal penghasilanNeto = annual.annualNeto();
        BigDecimal ptkp = annual.ptkpAmount();
        BigDecimal pkp = annual.pkp();
        BigDecimal pph21Terutang = annual.annualTax();
        BigDecimal pph21Dipotong = summary.totalPph21();
        BigDecimal pph21KurangBayar = pph21Terutang.subtract(pph21Dipotong);

        List<A1MonthlyBreakdown> monthlyBreakdown = yearlyDetails.stream()
                .map(d -> new A1MonthlyBreakdown(
                        d.getPayrollRun().getPayrollPeriod(),
                        d.getGrossSalary(),
                        d.getBonus(),
                        d.getThr(),
                        d.getBenefitInKind(),
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
                        summary.getTotalSalary(),
                        summary.totalBonusAndThr(),
                        summary.totalBenefitInKind(),
                        penghasilanBruto,
                        biayaJabatan,
                        summary.totalPensionContributions(),
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
                    summary.getTotalBruto(),
                    summary.totalPph21()
            ));
            totalGross = totalGross.add(summary.getTotalBruto());
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
            LocalDateTime postedAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<String> warnings
    ) {
        /** List rows: warnings need a ledger query per run, so the field is absent there. */
        public static PayrollRunResponse from(PayrollRun run) {
            return from(run, null);
        }

        public static PayrollRunResponse from(PayrollRun run, List<String> warnings) {
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
                    run.getPostedAt(),
                    warnings
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
            List<PayrollDetailResponse> details,
            List<PayrollAdditionResponse> additions,
            List<String> warnings
    ) {
        public static PayrollRunDetailResponse from(PayrollRun run, List<PayrollDetail> details,
                                                    List<PayrollAddition> additions, List<String> warnings) {
            return new PayrollRunDetailResponse(
                    run.getId(),
                    run.getPayrollPeriod(),
                    run.getStatus(),
                    run.getTotalGross(),
                    run.getTotalPph21(),
                    run.getEmployeeCount(),
                    details.stream().map(PayrollDetailResponse::from).toList(),
                    additions.stream().map(PayrollAdditionResponse::from).toList(),
                    warnings
            );
        }
    }

    public record PayrollDetailResponse(
            UUID id,
            UUID employeeId,
            String employeeName,
            BigDecimal baseSalary,
            BigDecimal bonus,
            BigDecimal thr,
            BigDecimal benefitInKind,
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
                    d.getBonus(),
                    d.getThr(),
                    d.getBenefitInKind(),
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

    public record PayrollAdditionRequest(
            @NotNull(message = "Karyawan wajib diisi") UUID employeeId,
            @NotNull(message = "Jenis wajib diisi") PayrollAdditionType type,
            @NotNull(message = "Jumlah wajib diisi")
            @Positive(message = "Jumlah harus lebih dari nol") BigDecimal amount,
            @Size(max = 255, message = "Keterangan maksimal 255 karakter") String description
    ) {}

    public record PayrollAdditionResponse(
            UUID id,
            UUID employeeId,
            String employeeName,
            PayrollAdditionType type,
            BigDecimal amount,
            String description
    ) {
        public static PayrollAdditionResponse from(PayrollAddition a) {
            return new PayrollAdditionResponse(a.getId(), a.getEmployee().getId(), a.getEmployee().getName(),
                    a.getAdditionType(), a.getAmount(), a.getDescription());
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
            BigDecimal gajiDanTunjangan,
            BigDecimal bonusDanThr,
            BigDecimal natura,
            BigDecimal penghasilanBruto,
            BigDecimal biayaJabatan,
            BigDecimal iuranPensiun,
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
            BigDecimal bonus,
            BigDecimal thr,
            BigDecimal benefitInKind,
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

    // ==================== SCHEDULE ====================

    @GetMapping("/schedule")
    @Operation(summary = "Get payroll schedule configuration",
            description = "Returns the current payroll schedule configuration, or 404 if not configured.")
    @ApiResponse(responseCode = "200", description = "Schedule configuration")
    @ApiResponse(responseCode = "404", description = "No schedule configured")
    public ResponseEntity<ScheduleResponse> getSchedule() {
        return payrollService.getSchedule()
                .map(s -> ResponseEntity.ok(ScheduleResponse.from(s)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/schedule")
    @PreAuthorize("hasAuthority('SCOPE_payroll:write') and hasAuthority('PAYROLL_CREATE') and hasAuthority('PAYROLL_APPROVE')")
    @Operation(summary = "Create or update payroll schedule",
            description = "Configures automated monthly payroll run creation. "
                    + "On the configured dayOfMonth, the scheduler creates a DRAFT payroll run, "
                    + "optionally calculates (BPJS + PPh 21 TER) and approves it. "
                    + "Posting is always manual — posting means payment has been made. "
                    + "Only one schedule exists; calling POST replaces the existing one.")
    @ApiResponse(responseCode = "200", description = "Schedule saved")
    public ResponseEntity<ScheduleResponse> saveSchedule(@Valid @RequestBody ScheduleRequest request) {
        log.info("API: Save payroll schedule - dayOfMonth={}, autoCalculate={}, autoApprove={}",
                request.dayOfMonth(), request.autoCalculate(), request.autoApprove());

        PayrollSchedule schedule = new PayrollSchedule();
        schedule.setDayOfMonth(request.dayOfMonth());
        schedule.setBaseSalary(request.baseSalary());
        schedule.setJkkRiskClass(request.jkkRiskClass());
        schedule.setAutoCalculate(request.autoCalculate());
        schedule.setAutoApprove(request.autoApprove());
        schedule.setActive(request.active() == null || request.active());

        PayrollSchedule saved = payrollService.saveSchedule(schedule);
        return ResponseEntity.ok(ScheduleResponse.from(saved));
    }

    @DeleteMapping("/schedule")
    @PreAuthorize("hasAuthority('SCOPE_payroll:write') and hasAuthority('PAYROLL_CREATE') and hasAuthority('PAYROLL_APPROVE')")
    @Operation(summary = "Delete payroll schedule",
            description = "Disables automated payroll run creation by removing the schedule configuration.")
    @ApiResponse(responseCode = "204", description = "Schedule deleted")
    public ResponseEntity<Void> deleteSchedule() {
        log.info("API: Delete payroll schedule");
        payrollService.deleteSchedule();
        return ResponseEntity.noContent().build();
    }

    // Schedule DTOs
    public record ScheduleRequest(
            @NotNull @Min(1) @Max(28) Integer dayOfMonth,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal baseSalary,
            @NotNull @Min(1) @Max(5) Integer jkkRiskClass,
            @NotNull Boolean autoCalculate,
            Boolean autoApprove,
            Boolean active
    ) {}

    public record ScheduleResponse(
            UUID id,
            int dayOfMonth,
            BigDecimal baseSalary,
            int jkkRiskClass,
            boolean autoCalculate,
            boolean autoApprove,
            boolean active
    ) {
        public static ScheduleResponse from(PayrollSchedule s) {
            return new ScheduleResponse(
                    s.getId(),
                    s.getDayOfMonth(),
                    s.getBaseSalary(),
                    s.getJkkRiskClass(),
                    s.getAutoCalculate(),
                    s.getAutoApprove(),
                    s.getActive()
            );
        }
    }
}
