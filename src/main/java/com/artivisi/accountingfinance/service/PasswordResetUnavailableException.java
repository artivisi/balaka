package com.artivisi.accountingfinance.service;

/** Password reset by email is not configured on this instance (app.password-reset.enabled=false). */
public class PasswordResetUnavailableException extends RuntimeException {

    public PasswordResetUnavailableException() {
        super("Reset password melalui email belum dikonfigurasi pada aplikasi ini. Hubungi administrator.");
    }
}
