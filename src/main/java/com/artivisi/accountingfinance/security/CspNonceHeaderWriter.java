package com.artivisi.accountingfinance.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.header.HeaderWriter;

/**
 * Custom CSP header writer that uses per-request nonce values.
 *
 * This replaces 'unsafe-inline' and 'unsafe-eval' with cryptographically
 * secure nonces, significantly improving XSS protection while maintaining
 * compatibility with Alpine.js and HTMX.
 */
public class CspNonceHeaderWriter implements HeaderWriter {

    private static final String CSP_HEADER = "Content-Security-Policy";

    @Override
    public void writeHeaders(HttpServletRequest request, HttpServletResponse response) {
        String nonce = (String) request.getAttribute(CspNonceFilter.CSP_NONCE_ATTRIBUTE);

        if (nonce == null) {
            throw new IllegalStateException("CSP nonce missing: CspNonceFilter did not run for this request");
        }

        // CSP with nonce-based scripts and styles - strict protection
        // Alpine CSP build + Alpine.data() components eliminate need for Function() constructor
        // See: https://alpinejs.dev/advanced/csp
        //
        // Both script-src and style-src use nonce-based protection:
        // - Dynamic styles use nonce'd <style> elements instead of style="" attributes
        // - All inline scripts require matching nonce
        // - No unsafe-inline or unsafe-eval allowed
        //
        // Style hashes for Alpine's internal inline styles:
        // - sha256-bsV5JivYxvGywDAZ22EZJKBFip65Ng9xoJVLbBg7bdo= = "display: none;" (x-cloak, x-show)
        // - sha256-ou12T4Lu3K6jhM7FOB2jdcFVyGsRVXgY4K7kE4tesk0= = "overflow: hidden;" (x-collapse)
        // - sha256-faU7yAF8NxuMTNEwVmBz+VcYeIoBQ2EMHW3WaVxCvnk= = HTMX indicator styles
        String cspPolicy = String.format(
            "default-src 'self'; " +
            "script-src 'self' 'nonce-%s' https://cdn.jsdelivr.net https://unpkg.com; " +
            "style-src 'self' 'nonce-%s' 'sha256-bsV5JivYxvGywDAZ22EZJKBFip65Ng9xoJVLbBg7bdo=' 'sha256-ou12T4Lu3K6jhM7FOB2jdcFVyGsRVXgY4K7kE4tesk0=' 'sha256-faU7yAF8NxuMTNEwVmBz+VcYeIoBQ2EMHW3WaVxCvnk=' https://cdn.jsdelivr.net https://fonts.googleapis.com; " +
            "font-src 'self' https://fonts.gstatic.com https://cdn.jsdelivr.net; " +
            "img-src 'self' data: blob:; " +
            "connect-src 'self'; " +
            "frame-ancestors 'none'; " +
            "form-action 'self'; " +
            "base-uri 'self'",
            nonce, nonce
        );

        response.setHeader(CSP_HEADER, cspPolicy);
    }
}
