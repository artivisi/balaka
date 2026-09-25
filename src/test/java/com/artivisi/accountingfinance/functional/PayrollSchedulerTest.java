package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.EmploymentStatus;
import com.artivisi.accountingfinance.entity.PayrollDetail;
import com.artivisi.accountingfinance.entity.PayrollRun;
import com.artivisi.accountingfinance.entity.PayrollSchedule;
import com.artivisi.accountingfinance.entity.PayrollStatus;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.EmployeeRepository;
import com.artivisi.accountingfinance.repository.PayrollDetailRepository;
import com.artivisi.accountingfinance.repository.PayrollRunRepository;
import com.artivisi.accountingfinance.service.PayrollService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PayrollService.executeScheduledPayroll with an unsaved PayrollSchedule: the singleton
 * payroll_schedule row (read by PayrollApiTest and the startup catch-up) is never written.
 * Periods 2029-xx are reserved for this class; the runs it creates are left in place.
 */
@DisplayName("Payroll Scheduler - Functional Tests")
@Import(ServiceTestDataInitializer.class)
class PayrollSchedulerTest extends PlaywrightTestBase {

    private static final BigDecimal BASE_SALARY = new BigDecimal("5000000");

    @Autowired
    private PayrollService payrollService;

    @Autowired
    private PayrollRunRepository payrollRunRepository;

    @Autowired
    private PayrollDetailRepository payrollDetailRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Test
    @DisplayName("executeScheduledPayroll creates an empty DRAFT when autoCalculate=false")
    void shouldCreateDraftOnly() {
        Optional<PayrollRun> result = payrollService.executeScheduledPayroll(
                YearMonth.of(2029, 1), schedule(false, false));

        assertThat(result).isPresent();
        PayrollRun run = persisted("2029-01");
        assertThat(run.getId()).isEqualTo(result.get().getId());
        assertThat(run.getStatus()).isEqualTo(PayrollStatus.DRAFT);
        assertThat(run.getEmployeeCount()).isZero();
        assertThat(payrollDetailRepository.findByPayrollRunId(run.getId())).isEmpty();
    }

    @Test
    @DisplayName("executeScheduledPayroll calculates every active employee at the schedule's base salary when autoCalculate=true")
    void shouldCreateAndCalculate() {
        int activeEmployees = employeeRepository.findByActiveTrueAndEmploymentStatus(EmploymentStatus.ACTIVE).size();

        Optional<PayrollRun> result = payrollService.executeScheduledPayroll(
                YearMonth.of(2029, 2), schedule(true, false));

        assertThat(result).isPresent();
        PayrollRun run = persisted("2029-02");
        assertThat(run.getStatus()).isEqualTo(PayrollStatus.CALCULATED);
        assertThat(run.getEmployeeCount()).isEqualTo(activeEmployees);
        assertThat(run.getTotalGross()).isEqualByComparingTo(BASE_SALARY.multiply(BigDecimal.valueOf(activeEmployees)));

        List<PayrollDetail> details = payrollDetailRepository.findByPayrollRunIdWithEmployee(run.getId());
        assertThat(details).hasSize(activeEmployees);
        assertThat(details).allSatisfy(d -> {
            assertThat(d.getBaseSalary()).isEqualByComparingTo(BASE_SALARY);
            assertThat(d.getGrossSalary()).isEqualByComparingTo(BASE_SALARY);
            assertThat(d.getJkkRiskClass()).isEqualTo(1);
        });
        assertThat(details).extracting(d -> d.getEmployee().getEmployeeId())
                .contains("EMP-001", "EMP-002", "EMP-003");
    }

    @Test
    @DisplayName("executeScheduledPayroll creates, calculates, and approves when both flags true")
    void shouldCreateCalculateAndApprove() {
        Optional<PayrollRun> result = payrollService.executeScheduledPayroll(
                YearMonth.of(2029, 3), schedule(true, true));

        assertThat(result).isPresent();
        PayrollRun run = persisted("2029-03");
        assertThat(run.getStatus()).isEqualTo(PayrollStatus.APPROVED);
        assertThat(run.getEmployeeCount()).isPositive();
        assertThat(payrollDetailRepository.findByPayrollRunId(run.getId())).hasSize(run.getEmployeeCount());
    }

    @Test
    @DisplayName("executeScheduledPayroll skips a period that already has a run and leaves that run unchanged")
    void shouldSkipExistingPeriod() {
        PayrollSchedule schedule = schedule(true, false);
        YearMonth period = YearMonth.of(2029, 4);

        Optional<PayrollRun> first = payrollService.executeScheduledPayroll(period, schedule);
        assertThat(first).isPresent();
        PayrollRun before = persisted("2029-04");

        Optional<PayrollRun> second = payrollService.executeScheduledPayroll(period, schedule(true, true));

        assertThat(second).isEmpty();
        PayrollRun after = persisted("2029-04");
        assertThat(after.getId()).isEqualTo(before.getId());
        assertThat(after.getStatus()).isEqualTo(PayrollStatus.CALCULATED);
        assertThat(after.getTotalGross()).isEqualByComparingTo(before.getTotalGross());
    }

    @Test
    @DisplayName("executeScheduledPayroll never posts: approved run has no journal transaction")
    void shouldNeverAutoPost() {
        Optional<PayrollRun> result = payrollService.executeScheduledPayroll(
                YearMonth.of(2029, 5), schedule(true, true));

        assertThat(result).isPresent();
        PayrollRun run = persisted("2029-05");
        assertThat(run.getStatus()).isEqualTo(PayrollStatus.APPROVED);
        assertThat(run.getTransaction()).isNull();
    }

    private PayrollRun persisted(String period) {
        return payrollRunRepository.findByPayrollPeriod(period)
                .orElseThrow(() -> new IllegalStateException("Payroll run not persisted for " + period));
    }

    private static PayrollSchedule schedule(boolean autoCalculate, boolean autoApprove) {
        PayrollSchedule schedule = new PayrollSchedule();
        schedule.setDayOfMonth(28);
        schedule.setBaseSalary(BASE_SALARY);
        schedule.setJkkRiskClass(1);
        schedule.setAutoCalculate(autoCalculate);
        schedule.setAutoApprove(autoApprove);
        schedule.setActive(true);
        return schedule;
    }
}
