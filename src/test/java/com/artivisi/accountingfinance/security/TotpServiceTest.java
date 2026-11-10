package com.artivisi.accountingfinance.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TOTP parameters are contract: an authenticator app configured by hand or by
 * the otpauth URI must produce codes this service accepts, and vice versa.
 */
@DisplayName("TOTP (RFC 6238, HMAC-SHA1)")
class TotpServiceTest {

    /** The RFC 6238 SHA-1 test secret, ASCII "12345678901234567890". */
    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    private final TotpService totpService = new TotpService();

    @Test
    @DisplayName("matches the RFC 6238 SHA-1 vectors")
    void matchesRfcVectors() {
        // RFC 6238 prints 8 digits; the 6-digit code is the same value modulo 10^6.
        assertThat(totpService.generateCode(RFC_SECRET, 1)).isEqualTo("287082");         // T=59
        assertThat(totpService.generateCode(RFC_SECRET, 37037036)).isEqualTo("081804");  // T=1111111109
        assertThat(totpService.generateCode(RFC_SECRET, 41152263)).isEqualTo("005924");  // T=1234567890
    }

    @Test
    @DisplayName("accepts the code of the current step and one step of clock skew")
    void acceptsCurrentAndAdjacentSteps() {
        long step = 12345678L;
        String code = totpService.generateCode(RFC_SECRET, step);

        assertThat(totpService.matchingStep(RFC_SECRET, code, step * 30)).isEqualTo(step);
        assertThat(totpService.matchingStep(RFC_SECRET, code, (step + 1) * 30)).isEqualTo(step);
        assertThat(totpService.matchingStep(RFC_SECRET, code, (step - 1) * 30)).isEqualTo(step);
    }

    @Test
    @DisplayName("rejects a code two steps away, a wrong code and a malformed code")
    void rejectsOutsideTheWindow() {
        long step = 12345678L;
        String code = totpService.generateCode(RFC_SECRET, step);

        assertThat(totpService.matchingStep(RFC_SECRET, code, (step + 2) * 30)).isEqualTo(-1L);
        assertThat(totpService.matchingStep(RFC_SECRET, "000000", step * 30)).isEqualTo(-1L);
        assertThat(totpService.matchingStep(RFC_SECRET, "12345", step * 30)).isEqualTo(-1L);
        assertThat(totpService.matchingStep(RFC_SECRET, "abcdef", step * 30)).isEqualTo(-1L);
        assertThat(totpService.matchingStep(RFC_SECRET, null, step * 30)).isEqualTo(-1L);
        assertThat(totpService.matchingStep("not-base32!", "123456", step * 30)).isEqualTo(-1L);
    }

    @Test
    @DisplayName("a generated secret is usable, distinct and decodes to 160 bits")
    void generatesFreshSecrets() {
        String first = totpService.generateSecret();
        String second = totpService.generateSecret();

        assertThat(first).isNotEqualTo(second);
        assertThat(first).matches("[A-Z2-7]+");
        assertThat(TotpService.base32Decode(first)).hasSize(20);

        long step = totpService.currentStep();
        assertThat(totpService.matchingStep(first, totpService.generateCode(first, step))).isEqualTo(step);
    }

    @Test
    @DisplayName("the otpauth URI states SHA1, 6 digits and a 30 second period")
    void otpAuthUriCarriesTheParameters() {
        String uri = totpService.getOtpAuthUri("ABCDEF", "budi", "Balaka");

        assertThat(uri).startsWith("otpauth://totp/");
        assertThat(uri).contains("secret=ABCDEF");
        assertThat(uri).contains("algorithm=SHA1");
        assertThat(uri).contains("digits=6");
        assertThat(uri).contains("period=30");
    }

    @Test
    @DisplayName("base32 round-trips arbitrary bytes")
    void base32RoundTrip() {
        byte[] data = "halo balaka".getBytes(StandardCharsets.UTF_8);
        assertThat(TotpService.base32Decode(TotpService.base32Encode(data))).isEqualTo(data);
    }
}
