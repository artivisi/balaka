package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.exception.MissingConfigurationException;
import com.artivisi.accountingfinance.service.DepreciationReportService;
import com.artivisi.accountingfinance.service.FiscalPeriodService;
import com.artivisi.accountingfinance.service.PayrollService;
import com.artivisi.accountingfinance.service.ReportService;
import com.artivisi.accountingfinance.service.SptTahunanExportService;
import com.artivisi.accountingfinance.service.TaxReportDetailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SPT checklist surfaces item failures")
class SptChecklistControllerTest {

    private static final int YEAR = 2095;

    @Mock private ReportService reportService;
    @Mock private TaxReportDetailService taxReportDetailService;
    @Mock private SptTahunanExportService sptTahunanExportService;
    @Mock private FiscalPeriodService fiscalPeriodService;
    @Mock private PayrollService payrollService;
    @Mock private DepreciationReportService depreciationReportService;

    @InjectMocks private SptChecklistController controller;

    @Test
    @DisplayName("A failing check is reported with its message, the other checks are still computed")
    void failingChecksAreReportedWithTheirMessage() {
        when(reportService.generateIncomeStatementExcludingClosing(LocalDate.of(YEAR, 1, 1), LocalDate.of(YEAR, 12, 31)))
                .thenReturn(new ReportService.IncomeStatementReport(LocalDate.of(YEAR, 1, 1), LocalDate.of(YEAR, 12, 31),
                        List.of(), List.of(), new BigDecimal("100000"), new BigDecimal("40000"), new BigDecimal("60000")));
        when(taxReportDetailService.findAdjustmentsByYear(YEAR)).thenReturn(List.of());
        when(taxReportDetailService.generateRekonsiliasiFiskal(YEAR))
                .thenThrow(new MissingConfigurationException("Akun Kredit Pajak belum dikonfigurasi"));
        when(fiscalPeriodService.findByYear(YEAR)).thenReturn(List.of());
        when(depreciationReportService.generateReport(YEAR))
                .thenThrow(new IllegalStateException("Kategori aset tanpa akun penyusutan"));
        when(payrollService.getEmployeesWithPayrollInYear(YEAR)).thenReturn(List.of());
        when(sptTahunanExportService.generateLossCarryforward(YEAR))
                .thenReturn(new SptTahunanExportService.LossCarryforwardReport(YEAR, List.of(), BigDecimal.ZERO));

        ConcurrentModel model = new ConcurrentModel();
        String view = controller.show(YEAR, model);

        assertThat(view).isEqualTo("reports/spt-checklist");
        SptChecklistController.ChecklistResult checklist =
                (SptChecklistController.ChecklistResult) model.getAttribute("checklist");
        assertThat(checklist.errors()).containsExactly(
                Map.entry("check-pph-badan", "Akun Kredit Pajak belum dikonfigurasi"),
                Map.entry("check-depreciation", "Kategori aset tanpa akun penyusutan"));
        assertThat(checklist.hasPphBadan()).isFalse();
        assertThat(checklist.hasDepreciation()).isFalse();
        assertThat(checklist.hasIncomeStatement()).isTrue();
        assertThat(checklist.totalRevenue()).isEqualByComparingTo("100000");
        assertThat(checklist.netIncome()).isEqualByComparingTo("60000");
    }
}
