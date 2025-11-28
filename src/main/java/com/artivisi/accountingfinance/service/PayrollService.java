package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.Employee;
import com.artivisi.accountingfinance.entity.EmploymentStatus;
import com.artivisi.accountingfinance.entity.PayrollDetail;
import com.artivisi.accountingfinance.entity.PayrollRun;
import com.artivisi.accountingfinance.entity.PayrollStatus;
import com.artivisi.accountingfinance.repository.EmployeeRepository;
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
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class PayrollService {

    private static final Logger log = LoggerFactory.getLogger(PayrollService.class);

    private static final int DEFAULT_JKK_RISK_CLASS = 1; // IT Services

    private final PayrollRunRepository payrollRunRepository;
    private final PayrollDetailRepository payrollDetailRepository;
    private final EmployeeRepository employeeRepository;
    private final BpjsCalculationService bpjsCalculationService;
    private final Pph21CalculationService pph21CalculationService;

    public PayrollService(
            PayrollRunRepository payrollRunRepository,
            PayrollDetailRepository payrollDetailRepository,
            EmployeeRepository employeeRepository,
            BpjsCalculationService bpjsCalculationService,
            Pph21CalculationService pph21CalculationService) {
        this.payrollRunRepository = payrollRunRepository;
        this.payrollDetailRepository = payrollDetailRepository;
        this.employeeRepository = employeeRepository;
        this.bpjsCalculationService = bpjsCalculationService;
        this.pph21CalculationService = pph21CalculationService;
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
            .orElseThrow(() -> new IllegalArgumentException("Payroll run tidak ditemukan"));

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
            .orElseThrow(() -> new IllegalArgumentException("Payroll run tidak ditemukan"));

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
            .orElseThrow(() -> new IllegalArgumentException("Payroll run tidak ditemukan"));

        if (!payrollRun.canCancel()) {
            throw new IllegalStateException("Payroll tidak dapat dibatalkan karena status: " + payrollRun.getStatus());
        }

        payrollRun.setStatus(PayrollStatus.CANCELLED);
        payrollRun.setCancelledAt(LocalDateTime.now());
        payrollRun.setCancelReason(reason);

        log.info("Cancelled payroll for period {}, reason: {}", payrollRun.getPayrollPeriod(), reason);

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
            .orElseThrow(() -> new IllegalArgumentException("Payroll run tidak ditemukan"));

        if (!payrollRun.isDraft()) {
            throw new IllegalStateException("Hanya payroll dengan status DRAFT yang dapat dihapus");
        }

        payrollRunRepository.delete(payrollRun);
        log.info("Deleted payroll run for period {}", payrollRun.getPayrollPeriod());
    }
}
