package com.artivisi.accountingfinance.service;

/** A reset link that cannot be used. */
public class PasswordResetTokenException extends RuntimeException {

    private final PasswordResetService.TokenState state;

    public PasswordResetTokenException(PasswordResetService.TokenState state) {
        super(messageFor(state));
        this.state = state;
    }

    public PasswordResetService.TokenState getState() {
        return state;
    }

    public static String messageFor(PasswordResetService.TokenState state) {
        return switch (state) {
            case USED -> "Tautan reset ini sudah dipakai. Minta tautan baru bila perlu.";
            case SUPERSEDED -> "Tautan reset ini digantikan oleh permintaan yang lebih baru. Gunakan tautan di email terakhir.";
            case EXPIRED -> "Tautan reset ini sudah kedaluwarsa. Minta tautan baru.";
            case UNKNOWN -> "Tautan reset tidak valid.";
            case VALID -> throw new IllegalArgumentException("A valid token is not an error");
        };
    }
}
