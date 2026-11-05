package com.artivisi.accountingfinance.config;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/**
 * Regulatory values for withholding on non-employee and vendor payments. Set in application.properties,
 * which carries the regulation cited for each value; startup fails when one is missing.
 * Only the PPh 21 values come from issue #43. The PPh 23 and PPh 26 values are unverified by a tax reviewer.
 */
@Configuration
@ConfigurationProperties(prefix = "app.withholding")
@Validated
@Getter
@Setter
public class WithholdingProperties {

    /** Share of gross that forms the PPh 21 bukan pegawai base. */
    @NotNull
    private BigDecimal pph21DppPercent;

    /** Surcharge on PPh 21 bukan pegawai when the payee has neither NPWP nor NIK. */
    @NotNull
    private BigDecimal pph21NoIdSurchargePercent;

    @NotNull
    private BigDecimal pph23RatePercent;

    /** Surcharge on PPh 23 when the payee has neither NPWP nor NIK. */
    @NotNull
    private BigDecimal pph23NoIdSurchargePercent;

    /** General PPh 26 rate, applied when no treaty rate backed by a valid DGT form applies. */
    @NotNull
    private BigDecimal pph26RatePercent;
}
