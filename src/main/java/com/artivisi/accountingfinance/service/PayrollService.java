package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.dto.FormulaContext;
import com.artivisi.accountingfinance.entity.Employee;
import com.artivisi.accountingfinance.entity.EmploymentStatus;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.PayrollDetail;
import com.artivisi.accountingfinance.entity.PayrollRun;
import com.artivisi.accountingfinance.entity.PayrollStatus;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.repository.EmployeeRepository;
import com.artivisi.accountingfinance.repository.JournalTemplateRepository;
import com.artivisi.accountingfinance.repository.PayrollDetailRepository;
import com.artivisi.accountingfinance.repository.PayrollRunRepository;
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

    // Payroll journal template ID (from V004 seed data)
    private static final UUID PAYROLL_TEMPLATE_ID = UUID.fromString("e0000000-0000-0000-0000-000000000014");

    private final PayrollRunRepository payrollRunRepository;
    private final PayrollDetailRepository payrollDetailRepository;
    private final EmployeeRepository employeeRepository;
    private final JournalTemplateRepository journalTemplateRepository;
    private final BpjsCalculationService bpjsCalculationService;
    private final Pph21CalculationService pph21CalculationService;
    private final TransactionService transactionService;

    public PayrollService(
            PayrollRunRepository payrollRunRepository,
            PayrollDetailRepository payrollDetailRepository,
            EmployeeRepository employeeRepository,
            JournalTemplateRepository journalTemplateRepository,
            BpjsCalculationService bpjsCalculationService,
            Pph21CalculationService pph21CalculationService,
            TransactionService transactionService) {
        this.payrollRunRepository = payrollRunRepository;
        this.payrollDetailRepository = payrollDetailRepository;
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
        payrollRun.getDetails().clear();

        // Get active employees
        List<Employee> activeEmployees = employeeRepository.findByActiveTrueAndEmploymentStatus(EmploymentStatus.ACTIVE);

        if (activeEmployees.isEmpty()) {
            throw new IllegalStateException("Tidak ada karyawan aktif untuk diproses");
        }

        // Calculate for each employee
        for (Employee employee : activeEmployees) {
            PayrollDetail detail = calculateEmployeePayroll(employee, baseSalary, jkkRiskClass);
            payrollRun.addDetail(detail);
        }

        // Update totals
        payrollRun.calculateTotals();
        payrollRun.setStatus(PayrollStatus.CALCULATED);

        log.info("Calculated payroll for {} employees, period {}", activeEmployees.size(), payrollRun.getPayrollPeriod());

        return payrollRunRepository.save(payrollRun);
    }

    /**
     * Calculate payroll for a single employee.
     */
    private PayrollDetail calculateEmployeePayroll(Employee employee, BigDecimal baseSalary, int jkkRiskClass) {
        PayrollDetail detail = new PayrollDetail();
        detail.setEmployee(employee);
        detail.setBaseSalary(baseSalary);
        detail.setGrossSalary(baseSalary); // For now, gross = base (can add allowances later)
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

        // Calculate PPh 21
        boolean hasNpwp = employee.getNpwp() != null && !employee.getNpwp().isBlank();
        var pph21Result = pph21CalculationService.calculate(baseSalary, employee.getPtkpStatus(), hasNpwp);
        detail.setPph21(pph21Result.monthlyPph21());

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

        // Sanitize reason for log injection prevention
        String sanitizedReason = reason != null ? reason.replaceAll("[\\r\\n]", " ") : "";
        log.info("Cancelled payroll for period {}, reason: {}", payrollRun.getPayrollPeriod(), sanitizedReason);

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

        // Get payroll template
        JournalTemplate payrollTemplate = journalTemplateRepository.findById(PAYROLL_TEMPLATE_ID)
            .orElseThrow(() -> new IllegalStateException("Template Post Gaji Bulanan tidak ditemukan"));

        // Calculate total employee BPJS from details
        List<PayrollDetail> details = payrollDetailRepository.findByPayrollRunIdWithEmployee(payrollRunId);
        BigDecimal totalEmployeeBpjs = details.stream()
            .map(PayrollDetail::getTotalEmployeeBpjs)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Create payroll-specific FormulaContext with extended variables
        BigDecimal totalBpjs = payrollRun.getTotalCompanyBpjs().add(totalEmployeeBpjs);
        FormulaContext payrollContext = FormulaContext.of(
            payrollRun.getTotalGross(), // amount = grossSalary for display
            Map.of(
                "grossSalary", payrollRun.getTotalGross(),
                "companyBpjs", payrollRun.getTotalCompanyBpjs(),
                "totalBpjs", totalBpjs,
                "pph21", payrollRun.getTotalPph21(),
                "netPay", payrollRun.getTotalNetPay()
            )
        );

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
