package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.dto.FormulaContext;
import com.artivisi.accountingfinance.entity.BenefitInKind;
import com.artivisi.accountingfinance.entity.Employee;
import com.artivisi.accountingfinance.entity.EmploymentStatus;
import com.artivisi.accountingfinance.entity.JournalTemplate;
import com.artivisi.accountingfinance.entity.PayrollAddition;
import com.artivisi.accountingfinance.entity.PayrollAdditionType;
import com.artivisi.accountingfinance.entity.PayrollDetail;
import com.artivisi.accountingfinance.entity.PayrollRun;
import com.artivisi.accountingfinance.entity.PayrollSchedule;
import com.artivisi.accountingfinance.entity.PayrollStatus;
import com.artivisi.accountingfinance.entity.Transaction;
import com.artivisi.accountingfinance.repository.BenefitInKindRepository;
import com.artivisi.accountingfinance.repository.EmployeeRepository;
import com.artivisi.accountingfinance.repository.PayrollAdditionRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    static final String VAR_BONUS = "bonus";
    static final String VAR_THR = "thr";
    static final String VAR_PPH21_OVERPAID = "pph21Overpaid";

    private final PayrollRunRepository payrollRunRepository;
    private final PayrollDetailRepository payrollDetailRepository;
    private final PayrollScheduleRepository payrollScheduleRepository;
    private final EmployeeRepository employeeRepository;
    private final BpjsCalculationService bpjsCalculationService;
    private final Pph21CalculationService pph21CalculationService;
    private final TransactionService transactionService;
    private final BenefitInKindRepository benefitInKindRepository;
    private final PayrollAdditionRepository payrollAdditionRepository;
    private final PayrollReconciliationService payrollReconciliationService;

    public PayrollService(
            PayrollRunRepository payrollRunRepository,
            PayrollDetailRepository payrollDetailRepository,
            PayrollScheduleRepository payrollScheduleRepository,
            EmployeeRepository employeeRepository,
            BpjsCalculationService bpjsCalculationService,
            Pph21CalculationService pph21CalculationService,
            TransactionService transactionService,
            BenefitInKindRepository benefitInKindRepository,
            PayrollAdditionRepository payrollAdditionRepository,
            PayrollReconciliationService payrollReconciliationService) {
        this.payrollRunRepository = payrollRunRepository;
        this.payrollDetailRepository = payrollDetailRepository;
        this.payrollScheduleRepository = payrollScheduleRepository;
        this.employeeRepository = employeeRepository;
        this.bpjsCalculationService = bpjsCalculationService;
        this.pph21CalculationService = pph21CalculationService;
        this.transactionService = transactionService;
        this.benefitInKindRepository = benefitInKindRepository;
        this.payrollAdditionRepository = payrollAdditionRepository;
        this.payrollReconciliationService = payrollReconciliationService;
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

        // A recalculation returns the run's benefit-in-kind to the pending pool before the details go
        releaseBenefitsInKind(payrollRun);

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

        // Income entered for someone who is not processed would silently vanish from PPh 21 and 1721-A1
        List<PayrollAddition> additions = payrollAdditionRepository.findByRunIdWithEmployee(payrollRunId);
        Set<UUID> activeIds = activeEmployees.stream().map(Employee::getId).collect(Collectors.toSet());
        for (PayrollAddition addition : additions) {
            if (!activeIds.contains(addition.getEmployee().getId())) {
                throw new IllegalStateException("Karyawan " + addition.getEmployee().getName()
                        + " tidak aktif tetapi punya " + addition.getAdditionType().getDisplayName()
                        + " pada payroll ini. Hapus baris tersebut atau aktifkan karyawan.");
            }
        }

        // Calculate for each employee
        YearMonth period = payrollRun.getPeriod();
        Map<UUID, List<BenefitInKind>> benefits = new java.util.HashMap<>();
        for (Employee employee : activeEmployees) {
            List<BenefitInKind> pending = benefitInKindRepository.findPendingForPayroll(
                    employee.getId(), period.atDay(1).withDayOfYear(1), period.atEndOfMonth());
            benefits.put(employee.getId(), pending);
            BigDecimal benefitInKind = pending.stream()
                    .map(BenefitInKind::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal bonus = sumAdditions(additions, employee, PayrollAdditionType.BONUS);
            BigDecimal thr = sumAdditions(additions, employee, PayrollAdditionType.THR);
            PayrollDetail detail = calculateEmployeePayroll(employee, baseSalary, bonus, thr, benefitInKind, jkkRiskClass, period);
            payrollRun.addDetail(detail);
        }

        // Update totals
        payrollRun.calculateTotals();
        payrollRun.setStatus(PayrollStatus.CALCULATED);

        log.info("Calculated payroll for {} employees, period {}", activeEmployees.size(), payrollRun.getPayrollPeriod());

        // Link after saving: the rows must reference the persisted detail instances
        PayrollRun saved = payrollRunRepository.save(payrollRun);
        for (PayrollDetail detail : saved.getDetails()) {
            benefits.get(detail.getEmployee().getId()).forEach(b -> b.setPayrollDetail(detail));
        }
        return saved;
    }

    private BigDecimal sumAdditions(List<PayrollAddition> additions, Employee employee, PayrollAdditionType type) {
        return additions.stream()
                .filter(a -> a.getAdditionType() == type && a.getEmployee().getId().equals(employee.getId()))
                .map(PayrollAddition::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Record a bonus or THR for one employee. A run that was already calculated is recalculated with the
     * same base salary and JKK class, so it can never be approved with figures that miss this income.
     */
    public PayrollAddition addAddition(UUID payrollRunId, UUID employeeId, PayrollAdditionType type,
                                       BigDecimal amount, String description) {
        PayrollRun payrollRun = editableRun(payrollRunId);
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Jumlah harus lebih dari nol");
        }
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Karyawan tidak ditemukan"));
        if (!employee.isActive() || employee.getEmploymentStatus() != EmploymentStatus.ACTIVE) {
            throw new IllegalArgumentException("Karyawan " + employee.getName() + " tidak aktif");
        }

        PayrollAddition addition = new PayrollAddition();
        addition.setPayrollRun(payrollRun);
        addition.setEmployee(employee);
        addition.setAdditionType(type);
        addition.setAmount(amount);
        addition.setDescription(description == null || description.isBlank() ? null : description.trim());
        PayrollAddition saved = payrollAdditionRepository.saveAndFlush(addition);

        recalculateIfCalculated(payrollRun);
        return saved;
    }

    public void removeAddition(UUID payrollRunId, UUID additionId) {
        PayrollRun payrollRun = editableRun(payrollRunId);
        PayrollAddition addition = payrollAdditionRepository.findById(additionId)
                .filter(a -> a.getPayrollRun().getId().equals(payrollRunId))
                .orElseThrow(() -> new IllegalArgumentException("Baris bonus/THR tidak ditemukan pada payroll ini"));
        payrollAdditionRepository.delete(addition);
        payrollAdditionRepository.flush();
        recalculateIfCalculated(payrollRun);
    }

    @Transactional(readOnly = true)
    public List<PayrollAddition> getAdditions(UUID payrollRunId) {
        return payrollAdditionRepository.findByRunIdWithEmployee(payrollRunId);
    }

    private PayrollRun editableRun(UUID payrollRunId) {
        PayrollRun payrollRun = payrollRunRepository.findById(payrollRunId)
                .orElseThrow(() -> new IllegalArgumentException(PAYROLL_RUN_NOT_FOUND));
        if (!payrollRun.canEdit()) {
            throw new IllegalStateException("Bonus dan THR tidak dapat diubah karena status payroll: " + payrollRun.getStatus());
        }
        return payrollRun;
    }

    private void recalculateIfCalculated(PayrollRun payrollRun) {
        if (payrollRun.isCalculated()) {
            PayrollDetail first = payrollDetailRepository.findByPayrollRunId(payrollRun.getId()).getFirst();
            calculatePayroll(payrollRun.getId(), first.getBaseSalary(), first.getJkkRiskClass());
        }
    }

    private void releaseBenefitsInKind(PayrollRun payrollRun) {
        List<BenefitInKind> linked = benefitInKindRepository.findByPayrollDetailPayrollRun(payrollRun);
        linked.forEach(b -> b.setPayrollDetail(null));
        benefitInKindRepository.flush();
    }

    /**
     * Calculate payroll for a single employee using TER method (PMK 168/2023).
     * Jan-Nov: TER rate × all income of the month: base salary, bonus, THR and natura. The rate is looked
     * up on that total, not on the base salary alone.
     * December: annual reconciliation (progressive brackets minus Jan-Nov withholdings).
     */
    private PayrollDetail calculateEmployeePayroll(Employee employee, BigDecimal baseSalary, BigDecimal bonus, BigDecimal thr,
                                                    BigDecimal benefitInKind, int jkkRiskClass, YearMonth period) {
        PayrollDetail detail = new PayrollDetail();
        detail.setEmployee(employee);
        detail.setBaseSalary(baseSalary);
        detail.setBonus(bonus);
        detail.setThr(thr);
        detail.setGrossSalary(baseSalary.add(bonus).add(thr));
        detail.setBenefitInKind(benefitInKind);
        detail.setJkkRiskClass(jkkRiskClass);

        // BPJS is on the fixed wage: bonus and THR are not part of it (PP 44/2015 Pasal 1 and
        // Perpres 82/2018 define upah as gaji pokok plus tunjangan tetap)
        var bpjsResult = bpjsCalculationService.calculate(baseSalary, jkkRiskClass, period);
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
                    priorMonths.stream().map(PayrollDetail::getTaxableGross).toList());
            allGrossAmounts.add(detail.getTaxableGross()); // Add December gross

            BigDecimal janNovPph21 = priorMonths.stream()
                    .map(PayrollDetail::getPph21)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal pensionContributions = priorMonths.stream()
                    .map(PayrollService::pensionContribution)
                    .reduce(pensionContribution(detail), BigDecimal::add);

            var decResult = pph21CalculationService.calculateDecemberReconciliation(
                    allGrossAmounts, pensionContributions, employee.getPtkpStatus(), janNovPph21);
            detail.setPph21(decResult.decemberPph21());
        } else {
            // Jan-Nov: TER rate lookup
            var terResult = pph21CalculationService.calculateTer(detail.getTaxableGross(), employee.getPtkpStatus());
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

        releaseBenefitsInKind(payrollRun);
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
     * - grossSalary: total regular salary, excluding bonus and THR (expense)
     * - bonus, thr: totals of the run (expense on their own accounts)
     * - companyBpjs: company BPJS contribution (expense)
     * - netPay: net pay to employees (liability)
     * - totalBpjs: total BPJS payable (liability)
     * - pph21: PPh 21 withheld (liability), zero when December reconciliation returns more than it withholds
     * - pph21Overpaid: net PPh 21 returned to employees in the run (debit on the same liability)
     */
    public PayrollRun postPayroll(UUID payrollRunId) {
        PayrollRun payrollRun = payrollRunRepository.findById(payrollRunId)
            .orElseThrow(() -> new IllegalArgumentException(PAYROLL_RUN_NOT_FOUND));

        if (!payrollRun.canPost()) {
            throw new IllegalStateException("Payroll harus dalam status APPROVED untuk di-posting");
        }

        JournalTemplate payrollTemplate = payrollReconciliationService.resolvePayrollTemplate();

        // Calculate BPJS totals split by Kesehatan vs Ketenagakerjaan from details
        List<PayrollDetail> details = payrollDetailRepository.findByPayrollRunIdWithEmployee(payrollRunId);

        BigDecimal companyBpjsKes = BigDecimal.ZERO;
        BigDecimal companyBpjsTk = BigDecimal.ZERO;
        BigDecimal employeeBpjsKes = BigDecimal.ZERO;
        BigDecimal employeeBpjsTk = BigDecimal.ZERO;
        BigDecimal totalBonus = BigDecimal.ZERO;
        BigDecimal totalThr = BigDecimal.ZERO;
        for (PayrollDetail d : details) {
            totalBonus = totalBonus.add(d.getBonus());
            totalThr = totalThr.add(d.getThr());
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
        variables.put(VAR_GROSS_SALARY, payrollRun.getTotalGross().subtract(totalBonus).subtract(totalThr));
        variables.put(VAR_BONUS, totalBonus);
        variables.put(VAR_THR, totalThr);
        variables.put(VAR_COMPANY_BPJS, payrollRun.getTotalCompanyBpjs());
        variables.put(VAR_COMPANY_BPJS_KES, companyBpjsKes);
        variables.put(VAR_COMPANY_BPJS_TK, companyBpjsTk);
        variables.put(VAR_TOTAL_BPJS, totalBpjsKes.add(totalBpjsTk));
        variables.put(VAR_TOTAL_BPJS_KES, totalBpjsKes);
        variables.put(VAR_TOTAL_BPJS_TK, totalBpjsTk);
        // A journal line cannot be negative: a net refund goes to the debit side of the same account
        variables.put(VAR_PPH21, payrollRun.getTotalPph21().max(BigDecimal.ZERO));
        variables.put(VAR_PPH21_OVERPAID, payrollRun.getTotalPph21().negate().max(BigDecimal.ZERO));
        variables.put(VAR_NET_PAY, payrollRun.getTotalNetPay());

        requireTemplateLines(payrollTemplate, variables, VAR_BONUS, VAR_THR, VAR_PPH21_OVERPAID);

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
        Transaction postedTransaction = transactionService.postSystem(savedTransaction.getId(), "system", payrollContext);

        // Update payroll run status
        payrollRun.setStatus(PayrollStatus.POSTED);
        payrollRun.setPostedAt(LocalDateTime.now());
        payrollRun.setTransaction(postedTransaction);

        log.info("Posted payroll for period {}, transaction: {}",
            payrollRun.getPayrollPeriod(), postedTransaction.getTransactionNumber());

        return payrollRunRepository.save(payrollRun);
    }

    private void requireTemplateLines(JournalTemplate template, Map<String, BigDecimal> variables, String... names) {
        for (String name : names) {
            Pattern usage = Pattern.compile("\\b" + name + "\\b");
            if (variables.get(name).signum() != 0 && template.getLines().stream()
                    .noneMatch(l -> usage.matcher(l.getFormula()).find())) {
                throw new IllegalStateException("Template " + template.getTemplateName()
                        + " tidak punya baris dengan formula " + name + ", tetapi payroll ini memuat nilai "
                        + variables.get(name));
            }
        }
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
        BigDecimal totalPensionContributions = details.stream()
            .map(PayrollService::pensionContribution)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalBenefitInKind = details.stream()
            .map(PayrollDetail::getBenefitInKind)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalBonusAndThr = details.stream()
            .map(d -> d.getBonus().add(d.getThr()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal unprocessedBenefitInKind = benefitInKindRepository.sumNotInPostedPayroll(
            employeeId, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));

        return new YearlyPayrollSummary(
            employee,
            year,
            details.size(),
            totalGross,
            totalBpjsEmployee,
            totalPensionContributions,
            totalPph21,
            totalBonusAndThr,
            totalBenefitInKind,
            unprocessedBenefitInKind
        );
    }

    /**
     * Employee-paid contributions deductible from PPh 21 neto: JHT and JP. BPJS Kesehatan is not one.
     */
    static BigDecimal pensionContribution(PayrollDetail detail) {
        return detail.getBpjsJhtEmployee().add(detail.getBpjsJpEmployee());
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
        BigDecimal totalPensionContributions,
        BigDecimal totalPph21,
        BigDecimal totalBonusAndThr,
        BigDecimal totalBenefitInKind,
        BigDecimal unprocessedBenefitInKind
    ) {
        /** Regular salary inside {@link #totalGross()}, which also holds bonus and THR. */
        public BigDecimal getTotalSalary() {
            return totalGross.subtract(totalBonusAndThr);
        }

        /** Gross income on 1721-A1: cash gross (salary, bonus, THR) plus benefit-in-kind that entered a posted run. */
        public BigDecimal getTotalBruto() {
            return totalGross.add(totalBenefitInKind);
        }

        public BigDecimal getNetIncome() {
            return totalGross.subtract(totalBpjsEmployee).subtract(totalPph21);
        }
    }
}
