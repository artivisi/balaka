package com.artivisi.accountingfinance.service;

import java.util.List;

/**
 * A password change refused for a reason the user can correct: wrong current password, mismatched
 * confirmation, or a new password that violates the policy. Nothing was changed.
 */
public class PasswordChangeException extends RuntimeException {

    private final transient List<String> errors;

    public PasswordChangeException(List<String> errors) {
        super(String.join(". ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
