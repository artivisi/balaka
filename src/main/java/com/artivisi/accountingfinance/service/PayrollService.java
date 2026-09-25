package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.dto.FormulaContext;
import com.artivisi.accountingfinance.entity.Employee;
import com.artivisi.accountingfinance.entity.EmploymentStatus;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.PayrollDetail;
import com.artivisi.accountingfinance.entity.PayrollRun;
import com.artivisi.accountingfinance.entity.PayrollSchedule;
import com.artivisi.accountingfinance.entity.PayrollStatus;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.repository.EmployeeRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.PayrollDetailRepository;
import com.artivisi.accountingfinance.repository.PayrollRunRepository;
import com.artivisi.accountingfinance.repository.PayrollScheduleRepository;
import com.artivisi.accountingfinance.security.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class PayrollService {

    private static final Logger log = LoggerFactory.getLogger(PayrollService.class);

    private static final int DEFAULT_JKK_RISK_CLASS = 1; // IT Services
    private static final String PAYROLL_RUN_NOT_FOUND = "Payroll run tidak ditemukan";

    // Formula variable names used in payroll journal template
    static final String VAR_GROSS_SALARY = "grossSalary";
    static final String VAR_COMPANY_BPJS = "companyBpjs";
    static final String VAR_COMPANY_BPJS_KES = "companyBpjsKes";
    static final String VAR_COMPANY_BPJS_TK = "companyBpjsTk";
    static final String VAR_TOTAL_BPJS = "totalBpjs";
    static final String VAR_TOTAL_BPJS_KES = "totalBpjsKes";
    static final String VAR_TOTAL_BPJS_TK = "totalBpjsTk";
    static final String VAR_PPH21 = "pph21";
    static final String VAR_NET_PAY = "netPay";

    @org.springframework.beans.factory.annotation.Value("${app.payroll.template-id}")
    private UUID payrollTemplateId;

    private final PayrollRunRepository payrollRunRepository;
    private final PayrollDetailRepository payrollDetailRepository;
    private final PayrollScheduleRepository payrollScheduleRepository;
    private final EmployeeRepository employeeRepository;
    private final JournalTemplateRepository journalTemplateRepository;
    private final BpjsCalculationService bpjsCalculationService;
    private final Pph21CalculationService pph21CalculationService;
    private final TransactionService transactionService;

    public PayrollService(
            PayrollRunRepository payrollRunRepository,
            PayrollDetailRepository payrollDetailRepository,
            PayrollScheduleRepository payrollScheduleRepository,
            EmployeeRepository employeeRepository,
            JournalTemplateRepository journalTemplateRepository,
            BpjsCalculationService bpjsCalculationService,
            Pph21CalculationService pph21CalculationService,
            TransactionService transactionService) {
        this.payrollRunRepository = payrollRunRepository;
        this.payrollDetailRepository = payrollDetailRepository;
        this.payrollScheduleRepository = payrollScheduleRepository;
        this.employeeRepository = employeeRepository;
        this.journalTemplateRepository = journalTemplateRepository;
        this.bpjsCalculationService = bpjsCalculationService;
        this.pph21CalculationService = pph21CalculationService;
        this.transactionService = transactionService;
    }

    /**
     * Create a new payroll run for a given period.
     */
    public PayrollRun createPayrollRun(YearMonth period) {
        if (payrollRunRepository.existsByPayrollPeriod(period.toString())) {
            throw new IllegalArgumentException("Payroll untuk periode " + period + " sudah ada");
        }

        PayrollRun payrollRun = new PayrollRun();
        payrollRun.setPeriod(period);
        payrollRun.setStatus(PayrollStatus.DRAFT);

        return payrollRunRepository.save(payrollRun);
    }

    /**
     * Calculate payroll for all active employees.
     */
    public PayrollRun calculatePayroll(UUID payrollRunId, BigDecimal baseSalary, int jkkRiskClass) {
        PayrollRun payrollRun = payrollRunRepository.findById(payrollRunId)
            .orElseThrow(() -> new IllegalArgumentException(PAYROLL_RUN_NOT_FOUND));

        if (!payrollRun.canEdit()) {
            throw new IllegalStateException("Payroll tidak dapat dikalkulasi karena status: " + payrollRun.getStatus());
        }

        // Clear existing details
        payrollDetailRepository.deleteByPayrollRun(payrollRun);
        payrollRun.clearDetails();
        // Hibernate flushes inserts before deletes; without this, re-inserting an employee's
        // detail violates uk_payroll_employee
        payrollDetailRepository.flush();

        // Get active employees
        List<Employee> activeEmployees = employeeRepository.findByActiveTrueAndEmploymentStatus(EmploymentStatus.ACTIVE);

        if (activeEmployees.isEmpty()) {
            throw new IllegalStateException("Tidak ada karyawan aktif untuk diproses");
        }

        // Calculate for each employee
        YearMonth period = payrollRun.getPeriod();
        for (Employee employee : activeEmployees) {
            PayrollDetail detail = calculateEmployeePayroll(employee, baseSalary, jkkRiskClass, period);
            payrollRun.addDetail(detail);
        }

        // Update totals
        payrollRun.calculateTotals();
        payrollRun.setStatus(PayrollStatus.CALCULATED);

        log.info("Calculated payroll for {} employees, period {}", activeEmployees.size(), payrollRun.getPayrollPeriod());

        return payrollRunRepository.save(payrollRun);
    }

    /**
     * Calculate payroll for a single employee using TER method (PMK 168/2023).
     * Jan-Nov: TER rate × gross salary.
     * December: annual reconciliation (progressive brackets minus Jan-Nov withholdings).
     */
    private PayrollDetail calculateEmployeePayroll(Employee employee, BigDecimal baseSalary,
                                                    int jkkRiskClass, YearMonth period) {
        PayrollDetail detail = new PayrollDetail();
        detail.setEmployee(employee);
        detail.setBaseSalary(baseSalary);
        detail.setGrossSalary(baseSalary);
        detail.setJkkRiskClass(jkkRiskClass);

        // Calculate BPJS
        var bpjsResult = bpjsCalculationService.calculate(baseSalary, jkkRiskClass);
        detail.setBpjsKesCompany(bpjsResult.kesehatanCompany());
        detail.setBpjsKesEmployee(bpjsResult.kesehatanEmployee());
        detail.setBpjsJkk(bpjsResult.jkk());
        detail.setBpjsJkm(bpjsResult.jkm());
        detail.setBpjsJhtCompany(bpjsResult.jhtCompany());
        detail.setBpjsJhtEmployee(bpjsResult.jhtEmployee());
        detail.setBpjsJpCompany(bpjsResult.jpCompany());
        detail.setBpjsJpEmployee(bpjsResult.jpEmployee());

        // Calculate PPh 21 using TER method
        if (period.getMonthValue() == 12) {
            // December: annual reconciliation
            List<PayrollDetail> priorMonths = payrollDetailRepository.findPriorMonthsInYear(
                    employee.getId(), String.valueOf(period.getYear()), period.toString());

            List<BigDecimal> allGrossAmounts = new java.util.ArrayList<>(
                    priorMonths.stream().map(PayrollDetail::getGrossSalary).toList());
            allGrossAmounts.add(baseSalary); // Add December gross

            BigDecimal janNovPph21 = priorMonths.stream()
                    .map(PayrollDetail::getPph21)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            var decResult = pph21CalculationService.calculateDecemberReconciliation(
                    allGrossAmounts, employee.getPtkpStatus(), janNovPph21);
            detail.setPph21(decResult.decemberPph21());
        } else {
            // Jan-Nov: TER rate lookup
            var terResult = pph21CalculationService.calculateTer(baseSalary, employee.getPtkpStatus());
            detail.setPph21(terResult.monthlyPph21());
        }

        // Calculate totals
        detail.calculateTotals();

        return detail;
    }

    /**
     * Approve payroll run for posting.
     */
    public PayrollRun approvePayroll(UUID payrollRunId) {
        PayrollRun payrollRun = payrollRunRepository.findById(payrollRunId)
            .orElseThrow(() -> new IllegalArgumentException(PAYROLL_RUN_NOT_FOUND));

        if (!payrollRun.isCalculated()) {
            throw new IllegalStateException("Payroll harus dalam status CALCULATED untuk di-approve");
        }

        payrollRun.setStatus(PayrollStatus.APPROVED);
        log.info("Approved payroll for period {}", payrollRun.getPayrollPeriod());

        return payrollRunRepository.save(payrollRun);
    }

    /**
     * Cancel payroll run.
     */
    public PayrollRun cancelPayroll(UUID payrollRunId, String reason) {
        PayrollRun payrollRun = payrollRunRepository.findById(payrollRunId)
            .orElseThrow(() -> new IllegalArgumentException(PAYROLL_RUN_NOT_FOUND));

        if (!payrollRun.canCancel()) {
            throw new IllegalStateException("Payroll tidak dapat dibatalkan karena status: " + payrollRun.getStatus());
        }

        payrollRun.setStatus(PayrollStatus.CANCELLED);
        payrollRun.setCancelledAt(LocalDateTime.now());
        payrollRun.setCancelReason(reason);

        if (log.isInfoEnabled()) {
            log.info("Cancelled payroll for period {}, reason: {}",
                    payrollRun.getPayrollPeriod(), LogSanitizer.sanitize(reason));
        }

        return payrollRunRepository.save(payrollRun);
    }

    /**
     * Post payroll to journal entries via Transaction.
     * Creates a Transaction using the payroll template with payroll-specific variables:
     * - grossSalary: total gross salary (expense)
     * - companyBpjs: company BPJS contribution (expense)
     * - netPay: net pay to employees (liability)
     * - totalBpjs: total BPJS payable (liability)
     * - pph21: PPh 21 withheld (liability)
     */
    public PayrollRun postPayroll(UUID payrollRunId) {
        PayrollRun payrollRun = payrollRunRepository.findById(payrollRunId)
            .orElseThrow(() -> new IllegalArgumentException(PAYROLL_RUN_NOT_FOUND));

        if (!payrollRun.canPost()) {
            throw new IllegalStateException("Payroll harus dalam status APPROVED untuk di-posting");
        }

        // Get payroll template by configured UUID, or fall back to lookup by name
        JournalTemplate payrollTemplate = journalTemplateRepository.findById(payrollTemplateId)
            .orElseGet(() -> journalTemplateRepository.findByTemplateNameAndIsCurrentVersionTrue("Post Gaji Bulanan")
                .orElseThrow(() -> new IllegalStateException("Template payroll tidak ditemukan (id: " + payrollTemplateId + ", name: Post Gaji Bulanan)")));

        // Calculate BPJS totals split by Kesehatan vs Ketenagakerjaan from details
        List<PayrollDetail> details = payrollDetailRepository.findByPayrollRunIdWithEmployee(payrollRunId);

        BigDecimal companyBpjsKes = BigDecimal.ZERO;
        BigDecimal companyBpjsTk = BigDecimal.ZERO;
        BigDecimal employeeBpjsKes = BigDecimal.ZERO;
        BigDecimal employeeBpjsTk = BigDecimal.ZERO;
        for (PayrollDetail d : details) {
            companyBpjsKes = companyBpjsKes.add(d.getBpjsKesCompany());
            companyBpjsTk = companyBpjsTk.add(d.getBpjsJkk()).add(d.getBpjsJkm())
                    .add(d.getBpjsJhtCompany()).add(d.getBpjsJpCompany());
            employeeBpjsKes = employeeBpjsKes.add(d.getBpjsKesEmployee());
            employeeBpjsTk = employeeBpjsTk.add(d.getBpjsJhtEmployee()).add(d.getBpjsJpEmployee());
        }

        BigDecimal totalBpjsKes = companyBpjsKes.add(employeeBpjsKes);
        BigDecimal totalBpjsTk = companyBpjsTk.add(employeeBpjsTk);

        // Create payroll-specific FormulaContext with extended variables
        // Templates can use either combined (companyBpjs, totalBpjs) or split (companyBpjsKes/Tk, totalBpjsKes/Tk)
        Map<String, BigDecimal> variables = new java.util.HashMap<>();
        variables.put(VAR_GROSS_SALARY, payrollRun.getTotalGross());
        variables.put(VAR_COMPANY_BPJS, payrollRun.getTotalCompanyBpjs());
        variables.put(VAR_COMPANY_BPJS_KES, companyBpjsKes);
        variables.put(VAR_COMPANY_BPJS_TK, companyBpjsTk);
        variables.put(VAR_TOTAL_BPJS, totalBpjsKes.add(totalBpjsTk));
        variables.put(VAR_TOTAL_BPJS_KES, totalBpjsKes);
        variables.put(VAR_TOTAL_BPJS_TK, totalBpjsTk);
        variables.put(VAR_PPH21, payrollRun.getTotalPph21());
        variables.put(VAR_NET_PAY, payrollRun.getTotalNetPay());

        FormulaContext payrollContext = FormulaContext.of(payrollRun.getTotalGross(), variables);

        // Create transaction
        String description = "Payroll " + payrollRun.getPeriodDisplayName();
        Transaction transaction = new Transaction();
        transaction.setJournalTemplate(payrollTemplate);
        transaction.setTransactionDate(payrollRun.getPeriodEnd());
        transaction.setAmount(payrollRun.getTotalGross());
        transaction.setDescription(description);
        transaction.setReferenceNumber("PAYROLL-" + payrollRun.getPayrollPeriod());

        // Create and post transaction with payroll context
        Transaction savedTransaction = transactionService.create(transaction, null);
        Transaction postedTransaction = transactionService.post(savedTransaction.getId(), "system", payrollContext);

        // Update payroll run status
        payrollRun.setStatus(PayrollStatus.POSTED);
        payrollRun.setPostedAt(LocalDateTime.now());
        payrollRun.setTransaction(postedTransaction);

        log.info("Posted payroll for period {}, transaction: {}",
            payrollRun.getPayrollPeriod(), postedTransaction.getTransactionNumber());

        return payrollRunRepository.save(payrollRun);
    }

    /**
     * Find payroll run by ID.
     */
    @Transactional(readOnly = true)
    public Optional<PayrollRun> findById(UUID id) {
        return payrollRunRepository.findById(id);
    }

    /**
     * Find payroll run by period.
     */
    @Transactional(readOnly = true)
    public Optional<PayrollRun> findByPeriod(String period) {
        return payrollRunRepository.findByPayrollPeriod(period);
    }

    /**
     * Find all payroll runs with pagination.
     */
    @Transactional(readOnly = true)
    public Page<PayrollRun> findAll(Pageable pageable) {
        return payrollRunRepository.findAllOrderByPeriodDesc(pageable);
    }

    /**
     * Find payroll runs by status.
     */
    @Transactional(readOnly = true)
    public Page<PayrollRun> findByStatus(PayrollStatus status, Pageable pageable) {
        return payrollRunRepository.findByStatusOptional(status, pageable);
    }

    /**
     * Get payroll details for a run.
     */
    @Transactional(readOnly = true)
    public List<PayrollDetail> getPayrollDetails(UUID payrollRunId) {
        return payrollDetailRepository.findByPayrollRunIdWithEmployee(payrollRunId);
    }

    /**
     * Check if period already has a payroll run.
     */
    @Transactional(readOnly = true)
    public boolean existsByPeriod(String period) {
        return payrollRunRepository.existsByPayrollPeriod(period);
    }

    /**
     * Delete a draft payroll run.
     */
    public void delete(UUID payrollRunId) {
        PayrollRun payrollRun = payrollRunRepository.findById(payrollRunId)
            .orElseThrow(() -> new IllegalArgumentException(PAYROLL_RUN_NOT_FOUND));

        if (!payrollRun.isDraft()) {
            throw new IllegalStateException("Hanya payroll dengan status DRAFT yang dapat dihapus");
        }

        payrollRunRepository.delete(payrollRun);
        log.info("Deleted payroll run for period {}", payrollRun.getPayrollPeriod());
    }

    /**
     * Get yearly payroll details for an employee (for 1721-A1).
     */
    @Transactional(readOnly = true)
    public List<PayrollDetail> getYearlyPayrollDetails(UUID employeeId, int year) {
        return payrollDetailRepository.findPostedByEmployeeIdAndYear(employeeId, String.valueOf(year));
    }

    /**
     * Get employees with posted payroll in a given year.
     */
    @Transactional(readOnly = true)
    public List<UUID> getEmployeesWithPayrollInYear(int year) {
        return payrollDetailRepository.findEmployeeIdsWithPostedPayrollInYear(String.valueOf(year));
    }

    /**
     * Calculate yearly totals for an employee (for 1721-A1).
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("java:S6809") // Internal call to readOnly method is safe - no transactional boundary issue
    public YearlyPayrollSummary getYearlyPayrollSummary(UUID employeeId, int year) {
        List<PayrollDetail> details = getYearlyPayrollDetails(employeeId, year);
        if (details.isEmpty()) {
            throw new IllegalArgumentException("Tidak ada data payroll untuk karyawan ini di tahun " + year);
        }

        Employee employee = details.get(0).getEmployee();
        BigDecimal totalGross = details.stream()
            .map(PayrollDetail::getGrossSalary)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPph21 = details.stream()
            .map(PayrollDetail::getPph21)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalBpjsEmployee = details.stream()
            .map(PayrollDetail::getTotalEmployeeBpjs)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new YearlyPayrollSummary(
            employee,
            year,
            details.size(),
            totalGross,
            totalBpjsEmployee,
            totalPph21
        );
    }

    // ==================== Schedule CRUD ====================

    public Optional<PayrollSchedule> getSchedule() {
        return payrollScheduleRepository.findCurrent();
    }

    public PayrollSchedule saveSchedule(PayrollSchedule schedule) {
        // Single-row: delete existing before saving
        payrollScheduleRepository.deleteAll();
        return payrollScheduleRepository.save(schedule);
    }

    public void deleteSchedule() {
        payrollScheduleRepository.deleteAll();
    }

    /**
     * Execute scheduled payroll for the given period.
     * Creates DRAFT, optionally calculates and approves.
     * Never auto-posts (posting = payment happened).
     *
     * @return the created PayrollRun, or empty if already exists
     */
    public Optional<PayrollRun> executeScheduledPayroll(YearMonth period, PayrollSchedule schedule) {
        String periodStr = period.toString();
        if (payrollRunRepository.existsByPayrollPeriod(periodStr)) {
            log.info("Scheduled payroll: period {} already exists, skipping", periodStr);
            return Optional.empty();
        }

        PayrollRun run = createPayrollRun(period);
        log.info("Scheduled payroll: created DRAFT for period {}", periodStr);

        if (Boolean.TRUE.equals(schedule.getAutoCalculate())) {
            run = calculatePayroll(run.getId(), schedule.getBaseSalary(), schedule.getJkkRiskClass());
            log.info("Scheduled payroll: calculated for period {}", periodStr);
        }

        if (Boolean.TRUE.equals(schedule.getAutoApprove())) {
            run = approvePayroll(run.getId());
            log.info("Scheduled payroll: approved for period {}", periodStr);
        }

        return Optional.of(run);
    }

    /**
     * Record for yearly payroll summary.
     */
    public record YearlyPayrollSummary(
        Employee employee,
        int year,
        int monthCount,
        BigDecimal totalGross,
        BigDecimal totalBpjsEmployee,
        BigDecimal totalPph21
    ) {
        public BigDecimal getNetIncome() {
            return totalGross.subtract(totalBpjsEmployee).subtract(totalPph21);
        }
    }
}
