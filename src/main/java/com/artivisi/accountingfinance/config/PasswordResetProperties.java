package com.artivisi.accountingfinance.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Password reset by email (issue #61). Values are set in application.properties; when {@code enabled}
 * is true, PasswordResetService refuses to start without SMTP settings, a sender and a base URL.
 */
@Configuration
@ConfigurationProperties(prefix = "app.password-reset")
@Validated
@Getter
@Setter
public class PasswordResetProperties {

    /** Off unless outbound email is configured. When off, the login page shows no reset link. */
    private boolean enabled;

    /**
     * Public URL of this instance (scheme and host, no trailing path), used to build the link in the
     * email. Configured rather than taken from the request, whose Host header the client controls.
     */
    private String baseUrl;

    /** Sender address. With Gmail it must be the authenticated account or a verified alias. */
    private String from;

    @NotNull
    private Duration tokenValidity;

    /** Reset requests accepted per client IP within {@link #rateWindow}. */
    @Positive
    private int maxRequestsPerIp;

    /** Reset emails sent per account within {@link #rateWindow}; further requests are answered the same way but send nothing. */
    @Positive
    private int maxRequestsPerAccount;

    @NotNull
    private Duration rateWindow;
}
