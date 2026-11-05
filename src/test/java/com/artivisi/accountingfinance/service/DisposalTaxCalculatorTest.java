package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.DisposalType;
import com.artivisi.accountingfinance.service.DisposalTaxCalculator.DisposalTaxEffect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Disposal tax effect")
class DisposalTaxCalculatorTest {

    private final DisposalTaxCalculator calculator = new DisposalTaxCalculator(new BigDecimal("11"));

    @Test
    @DisplayName("Sale above book value: gain is proceeds minus book value and adds to taxable income; PPN on the proceeds")
    void saleWithGain() {
        DisposalTaxEffect effect = calculator.calculate(DisposalType.SOLD, new BigDecimal("13000000"), new BigDecimal("11500000"));

        assertThat(effect.gainLoss()).isEqualByComparingTo("1500000");
        assertThat(effect.taxableIncomeEffect()).isEqualByComparingTo("1500000");
        assertThat(effect.fiscalCorrection()).isEqualByComparingTo("0");
        assertThat(effect.estimatedPpn()).isEqualByComparingTo("1430000");
        assertThat(effect.note()).contains("belum diverifikasi");
    }

    @Test
    @DisplayName("Sale below book value: negative gain reduces taxable income; PPN rounds half up to the cent")
    void saleWithLoss() {
        DisposalTaxEffect effect = calculator.calculate(DisposalType.SOLD, new BigDecimal("1234.57"), new BigDecimal("2000"));

        assertThat(effect.gainLoss()).isEqualByComparingTo("-765.43");
        assertThat(effect.taxableIncomeEffect()).isEqualByComparingTo("-765.43");
        assertThat(effect.estimatedPpn()).isEqualByComparingTo("135.80");
    }

    @Test
    @DisplayName("Sale at book value: no gain, no effect on taxable income")
    void saleAtBookValue() {
        DisposalTaxEffect effect = calculator.calculate(DisposalType.SOLD, new BigDecimal("5000"), new BigDecimal("5000"));

        assertThat(effect.gainLoss()).isEqualByComparingTo("0");
        assertThat(effect.taxableIncomeEffect()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Gift: no proceeds, loss equals book value, fully corrected back, so taxable income does not change")
    void gift() {
        DisposalTaxEffect effect = calculator.calculate(DisposalType.GIFTED, null, new BigDecimal("11500000"));

        assertThat(effect.proceeds()).isEqualByComparingTo("0");
        assertThat(effect.gainLoss()).isEqualByComparingTo("-11500000");
        assertThat(effect.fiscalCorrection()).isEqualByComparingTo("11500000");
        assertThat(effect.taxableIncomeEffect()).isEqualByComparingTo("0");
        assertThat(effect.estimatedPpn()).isNull();
        assertThat(effect.note()).contains("belum diverifikasi");
    }

    @Test
    @DisplayName("Write-off: book value reduces taxable income, no correction")
    void writeOff() {
        DisposalTaxEffect effect = calculator.calculate(DisposalType.WRITTEN_OFF, BigDecimal.ZERO, new BigDecimal("6000000"));

        assertThat(effect.gainLoss()).isEqualByComparingTo("-6000000");
        assertThat(effect.taxableIncomeEffect()).isEqualByComparingTo("-6000000");
        assertThat(effect.fiscalCorrection()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Transfer: gain or loss computed, fiscal effect left undetermined")
    void transfer() {
        DisposalTaxEffect effect = calculator.calculate(DisposalType.TRANSFERRED, null, new BigDecimal("6000000"));

        assertThat(effect.gainLoss()).isEqualByComparingTo("-6000000");
        assertThat(effect.taxableIncomeEffect()).isNull();
        assertThat(effect.fiscalCorrection()).isNull();
    }

    @Test
    @DisplayName("Sale needs positive proceeds; other types refuse proceeds; type and book value are required")
    void validation() {
        BigDecimal book = new BigDecimal("100");
        assertThatThrownBy(() -> calculator.calculate(DisposalType.SOLD, null, book)).hasMessageContaining("Hasil penjualan wajib");
        assertThatThrownBy(() -> calculator.calculate(DisposalType.SOLD, BigDecimal.ZERO, book)).hasMessageContaining("Hasil penjualan wajib");
        assertThatThrownBy(() -> calculator.calculate(DisposalType.GIFTED, BigDecimal.ONE, book)).hasMessageContaining("hanya untuk tipe SOLD");
        assertThatThrownBy(() -> calculator.calculate(DisposalType.WRITTEN_OFF, BigDecimal.ONE, book)).hasMessageContaining("hanya untuk tipe SOLD");
        assertThatThrownBy(() -> calculator.calculate(DisposalType.TRANSFERRED, BigDecimal.ONE, book)).hasMessageContaining("hanya untuk tipe SOLD");
        assertThatThrownBy(() -> calculator.calculate(null, null, book)).hasMessageContaining("Tipe pelepasan wajib");
        assertThatThrownBy(() -> calculator.calculate(DisposalType.GIFTED, null, null)).hasMessageContaining("Nilai buku wajib");
    }
}
