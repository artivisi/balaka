package com.artivisi.accountingfinance.security;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Password complexity validator implementing security requirements:
 * - Minimum 12 characters
 * - At least one uppercase letter
 * - At least one lowercase letter
 * - At least one digit
 * - At least one special character
 */
@Component
public class PasswordValidator {

    public static final int MIN_LENGTH = 12;

    // Use find() instead of matches() with .* to avoid ReDoS
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d");
    private static final Pattern SPECIAL_PATTERN = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]");

    /**
     * Validates password against complexity requirements.
     *
     * @param password the password to validate
     * @return PasswordValidationResult containing validation status and error messages
     */
    public PasswordValidationResult validate(String password) {
        List<String> errors = new ArrayList<>();

        if (password == null || password.isEmpty()) {
            errors.add("Password wajib diisi");
            return new PasswordValidationResult(false, errors);
        }

        if (password.length() < MIN_LENGTH) {
            errors.add("Password minimal " + MIN_LENGTH + " karakter");
        }

        if (!UPPERCASE_PATTERN.matcher(password).find()) {
            errors.add("Password harus mengandung minimal satu huruf besar");
        }

        if (!LOWERCASE_PATTERN.matcher(password).find()) {
            errors.add("Password harus mengandung minimal satu huruf kecil");
        }

        if (!DIGIT_PATTERN.matcher(password).find()) {
            errors.add("Password harus mengandung minimal satu angka");
        }

        if (!SPECIAL_PATTERN.matcher(password).find()) {
            errors.add("Password harus mengandung minimal satu karakter khusus (!@#$%^&* dll)");
        }

        return new PasswordValidationResult(errors.isEmpty(), errors);
    }

    /**
     * Quick check if password meets complexity requirements.
     *
     * @param password the password to check
     * @return true if password meets all requirements
     */
    public boolean isValid(String password) {
        return validate(password).isValid();
    }

    /**
     * Result record containing validation status and error messages.
     */
    public record PasswordValidationResult(boolean isValid, List<String> errors) {

        /**
         * Returns combined error message for display.
         */
        public String getErrorMessage() {
            return String.join(". ", errors);
        }
    }
}
