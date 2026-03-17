package com.artivisi.accountingfinance.functional;

import com.artivisi.accountingfinance.entity.PayrollRun;
import com.artivisi.accountingfinance.entity.PayrollSchedule;
import com.artivisi.accountingfinance.entity.PayrollStatus;
import com.artivisi.accountingfinance.functional.service.ServiceTestDataInitializer;
import com.artivisi.accountingfinance.repository.PayrollRunRepository;
import com.artivisi.accountingfinance.repository.PayrollScheduleRepository;
import com.artivisi.accountingfinance.service.PayrollService;
import com.artivisi.accountingfinance.ui.PlaywrightTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Payroll Scheduler - Functional Tests")
@Import(ServiceTestDataInitializer.class)
class PayrollSchedulerTest extends PlaywrightTestBase {

    @Autowired
    private PayrollService payrollService;

    @Autowired
    private PayrollRunRepository payrollRunRepository;

    @Autowired
    private PayrollScheduleRepository payrollScheduleRepository;

    @BeforeEach
    void setupAndLogin() {
        loginAsAdmin();
        // Clean up any schedule from previous tests
        payrollScheduleRepository.deleteAll();
    }

    @AfterEach
    void cleanup() {
        // Clean up payroll runs created during tests
        payrollRunRepository.findAll().stream()
                .filter(r -> r.getPayrollPeriod().startsWith("2029-"))
                .forEach(r -> payrollRunRepository.delete(r));
        payrollScheduleRepository.deleteAll();
    }

    @Test
    @DisplayName("executeScheduledPayroll creates DRAFT when autoCalculate=false")
    void shouldCreateDraftOnly() {
        PayrollSchedule schedule = createSchedule(false, false);

        Optional<PayrollRun> result = payrollService.executeScheduledPayroll(
                YearMonth.of(2029, 1), schedule);

        assertThat(result).isPresent();
        PayrollRun run = result.get();
        assertThat(run.getPayrollPeriod()).isEqualTo("2029-01");
        assertThat(run.getStatus()).isEqualTo(PayrollStatus.DRAFT);
        assertThat(run.getEmployeeCount()).isZero();
    }

    @Test
    @DisplayName("executeScheduledPayroll creates and calculates when autoCalculate=true")
    void shouldCreateAndCalculate() {
        PayrollSchedule schedule = createSchedule(true, false);

        Optional<PayrollRun> result = payrollService.executeScheduledPayroll(
                YearMonth.of(2029, 2), schedule);

        assertThat(result).isPresent();
        PayrollRun run = result.get();
        assertThat(run.getStatus()).isEqualTo(PayrollStatus.CALCULATED);
        assertThat(run.getEmployeeCount()).isGreaterThanOrEqualTo(1);
        assertThat(run.getTotalGross()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("executeScheduledPayroll creates, calculates, and approves when both flags true")
    void shouldCreateCalculateAndApprove() {
        PayrollSchedule schedule = createSchedule(true, true);

        Optional<PayrollRun> result = payrollService.executeScheduledPayroll(
                YearMonth.of(2029, 3), schedule);

        assertThat(result).isPresent();
        PayrollRun run = result.get();
        assertThat(run.getStatus()).isEqualTo(PayrollStatus.APPROVED);
    }

    @Test
    @DisplayName("executeScheduledPayroll skips if period already exists")
    void shouldSkipExistingPeriod() {
        PayrollSchedule schedule = createSchedule(true, false);
        YearMonth period = YearMonth.of(2029, 4);

        // First call creates the run
        Optional<PayrollRun> first = payrollService.executeScheduledPayroll(period, schedule);
        assertThat(first).isPresent();

        // Second call should skip
        Optional<PayrollRun> second = payrollService.executeScheduledPayroll(period, schedule);
        assertThat(second).isEmpty();
    }

    @Test
    @DisplayName("executeScheduledPayroll never auto-posts")
    void shouldNeverAutoPost() {
        PayrollSchedule schedule = createSchedule(true, true);

        Optional<PayrollRun> result = payrollService.executeScheduledPayroll(
                YearMonth.of(2029, 5), schedule);

        assertThat(result).isPresent();
        // Even with all flags true, status should be APPROVED, not POSTED
        assertThat(result.get().getStatus()).isEqualTo(PayrollStatus.APPROVED);
    }

    private PayrollSchedule createSchedule(boolean autoCalculate, boolean autoApprove) {
        PayrollSchedule schedule = new PayrollSchedule();
        schedule.setDayOfMonth(28);
        schedule.setBaseSalary(new BigDecimal("5000000"));
        schedule.setJkkRiskClass(1);
        schedule.setAutoCalculate(autoCalculate);
        schedule.setAutoApprove(autoApprove);
        schedule.setActive(true);
        return payrollScheduleRepository.save(schedule);
    }
}
