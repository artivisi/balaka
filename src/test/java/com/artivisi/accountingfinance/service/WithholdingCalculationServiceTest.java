package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.config.WithholdingProperties;
import com.artivisi.accountingfinance.entity.Vendor;
import com.artivisi.accountingfinance.enums.WithholdingRateBasis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Withholding calculation")
class WithholdingCalculationServiceTest {

    private static final BigDecimal NONE = BigDecimal.ZERO;
    private WithholdingCalculationService service;

    @BeforeEach
    void setUp() {
        WithholdingProperties properties = new WithholdingProperties();
        properties.setPph21DppPercent(new BigDecimal("50"));
        properties.setPph21NoIdSurchargePercent(new BigDecimal("20"));
        properties.setPph23RatePercent(new BigDecimal("2"));
        properties.setPph23NoIdSurchargePercent(new BigDecimal("100"));
        properties.setPph26RatePercent(new BigDecimal("20"));
        service = new WithholdingCalculationService(properties, new Pph21CalculationService());
    }

    /**
     * DPP = gross / 2. Boundaries of DPP: 60 jt, 250 jt, 500 jt, 5 M. Tax on those boundaries: 3 jt, 31.5 jt, 94 jt, 1.444 M.
     */
    @ParameterizedTest(name = "gross {0}: tax {2}, without ID {3}")
    @CsvSource({
            "10000000,    5000000,     250000,     300000",
            "120000000,   60000000,    3000000,    3600000",
            "120000002,   60000001,    3000000,    3600000",
            "200000000,   100000000,   9000000,    10800000",
            "500000000,   250000000,   31500000,   37800000",
            "800000000,   400000000,   69000000,   82800000",
            "1000000000,  500000000,   94000000,   112800000",
            "4000000000,  2000000000,  544000000,  652800000",
            "10000000000, 5000000000,  1444000000, 1732800000",
            "12000000000, 6000000000,  1794000000, 2152800000",
    })
    void pph21NonEmployeeAcrossBrackets(BigDecimal gross, BigDecimal dpp, BigDecimal tax, BigDecimal taxNoId) {
        WithholdingCalculationService.Withholding withId = service.pph21NonEmployee(gross, NONE, true);
        assertThat(withId.dpp()).isEqualByComparingTo(dpp);
        assertThat(withId.tax()).isEqualByComparingTo(tax);
        assertThat(withId.basis()).isEqualTo(WithholdingRateBasis.PPH21_PASAL_17);

        WithholdingCalculationService.Withholding withoutId = service.pph21NonEmployee(gross, NONE, false);
        assertThat(withoutId.dpp()).isEqualByComparingTo(dpp);
        assertThat(withoutId.tax()).isEqualByComparingTo(taxNoId);
        assertThat(withoutId.basis()).isEqualTo(WithholdingRateBasis.PPH21_PASAL_17_NO_ID);
    }

    @Test
    @DisplayName("Effective rate on DPP is the bracket rate, or the mix across brackets")
    void effectiveRate() {
        assertThat(service.pph21NonEmployee(new BigDecimal("10000000"), NONE, true).rate()).isEqualByComparingTo("5.00");
        assertThat(service.pph21NonEmployee(new BigDecimal("10000000"), NONE, false).rate()).isEqualByComparingTo("6.00");
        assertThat(service.pph21NonEmployee(new BigDecimal("200000000"), NONE, true).rate()).isEqualByComparingTo("9.00");
    }

    @Test
    @DisplayName("A later payment in the masa starts where earlier ones ended; split payments total the tax of their sum")
    void priorGrossMovesUpTheBrackets() {
        assertThat(service.pph21NonEmployee(new BigDecimal("100000000"), new BigDecimal("100000000"), true).tax())
                .isEqualByComparingTo("6500000");
        assertThat(service.pph21NonEmployee(new BigDecimal("100000000"), new BigDecimal("100000000"), false).tax())
                .isEqualByComparingTo("7800000");
        assertThat(service.pph21NonEmployee(new BigDecimal("200000000"), new BigDecimal("400000000"), true).tax())
                .isEqualByComparingTo("20000000");
    }

    @Test
    @DisplayName("PPh 23: configured rate, doubled without an ID (values unverified)")
    void pph23() {
        WithholdingCalculationService.Withholding withId = service.pph23(new BigDecimal("10000000"), true);
        assertThat(withId.rate()).isEqualByComparingTo("2.00");
        assertThat(withId.tax()).isEqualByComparingTo("200000");
        assertThat(withId.basis()).isEqualTo(WithholdingRateBasis.PPH23_STANDARD);

        WithholdingCalculationService.Withholding withoutId = service.pph23(new BigDecimal("10000000"), false);
        assertThat(withoutId.rate()).isEqualByComparingTo("4.00");
        assertThat(withoutId.tax()).isEqualByComparingTo("400000");
        assertThat(withoutId.basis()).isEqualTo(WithholdingRateBasis.PPH23_NO_ID);
    }

    @Test
    @DisplayName("PPh 26: general rate; treaty rate only with a DGT form valid on the payment date")
    void pph26() {
        LocalDate date = LocalDate.of(2132, 6, 15);
        BigDecimal gross = new BigDecimal("10000000");

        Vendor noTreaty = new Vendor();
        WithholdingCalculationService.Withholding general = service.pph26(gross, noTreaty, date);
        assertThat(general.tax()).isEqualByComparingTo("2000000");
        assertThat(general.basis()).isEqualTo(WithholdingRateBasis.PPH26_STANDARD);

        Vendor treaty = new Vendor();
        treaty.setTreatyRate(new BigDecimal("10"));
        treaty.setDgtFormNumber("DGT-1");
        treaty.setDgtValidUntil(date);
        WithholdingCalculationService.Withholding reduced = service.pph26(gross, treaty, date);
        assertThat(reduced.rate()).isEqualByComparingTo("10.00");
        assertThat(reduced.tax()).isEqualByComparingTo("1000000");
        assertThat(reduced.basis()).isEqualTo(WithholdingRateBasis.PPH26_TREATY);

        WithholdingCalculationService.Withholding expired = service.pph26(gross, treaty, date.plusDays(1));
        assertThat(expired.rate()).isEqualByComparingTo("20.00");
        assertThat(expired.tax()).isEqualByComparingTo("2000000");
        assertThat(expired.basis()).isEqualTo(WithholdingRateBasis.PPH26_TREATY_DGT_INVALID);

        treaty.setDgtFormNumber(" ");
        assertThat(service.pph26(gross, treaty, date).basis()).isEqualTo(WithholdingRateBasis.PPH26_TREATY_DGT_INVALID);
    }
}
