package com.artivisi.accountingfinance.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CspNonceHeaderWriter Tests")
class CspNonceHeaderWriterTest {

    private final CspNonceHeaderWriter writer = new CspNonceHeaderWriter();

    @Test
    @DisplayName("Should put the request nonce into script-src and style-src")
    void shouldWriteRequestNonce() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(CspNonceFilter.CSP_NONCE_ATTRIBUTE, "abc123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.writeHeaders(request, response);

        String csp = response.getHeader("Content-Security-Policy");
        assertThat(csp).contains("script-src 'self' 'nonce-abc123' ");
        assertThat(csp).contains("style-src 'self' 'nonce-abc123' ");
        assertThat(csp).doesNotContain("unsafe-inline").doesNotContain("unsafe-eval");
    }

    @Test
    @DisplayName("Should fail instead of writing a placeholder nonce when the nonce filter did not run")
    void shouldRejectMissingNonce() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> writer.writeHeaders(new MockHttpServletRequest(), response))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("CSP nonce missing: CspNonceFilter did not run for this request");
        assertThat(response.getHeader("Content-Security-Policy")).isNull();
    }
}
