package com.artivisi.accountingfinance.service;

/** Too many reset requests from one client address within the configured window. */
public class PasswordResetRateLimitedException extends RuntimeException {

    public PasswordResetRateLimitedException() {
        super("Terlalu banyak permintaan reset password. Coba lagi nanti.");
    }
}
