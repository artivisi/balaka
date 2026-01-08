package com.artivisi.accountingfinance.security;

/**
 * Utility class for sanitizing user input before logging.
 * Prevents log injection/forging attacks where attackers embed
 * newlines or control characters in input to create fake log entries.
 */
public final class LogSanitizer {

    private static final int MAX_LOG_LENGTH = 500;
    private static final String TRUNCATED_SUFFIX = "...[truncated]";

    private LogSanitizer() {
        // Utility class
    }

    /**
     * Sanitizes input for safe logging.
     * Removes newlines, control characters, and limits length to prevent:
     * 1. Log injection (fake log entries via newlines)
     * 2. Log forging (manipulating log appearance)
     * 3. Log flooding (excessive length)
     *
     * @param input the user-provided input to sanitize
     * @return sanitized string safe for logging
     */
    public static String sanitize(String input) {
        if (input == null) {
            return "null";
        }

        // Remove newlines and carriage returns (prevents log injection)
        // Replace tabs with spaces
        // Remove other control characters (0x00-0x1f, 0x7f)
        String sanitized = input
                .replace('\r', '_')
                .replace('\n', '_')
                .replace('\t', ' ')
                .replaceAll("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f\\x7f]", "?");

        // Limit length to prevent log flooding
        if (sanitized.length() > MAX_LOG_LENGTH) {
            return sanitized.substring(0, MAX_LOG_LENGTH) + TRUNCATED_SUFFIX;
        }

        return sanitized;
    }

    /**
     * Sanitizes a username for logging.
     * Applies stricter validation for usernames.
     *
     * @param username the username to sanitize
     * @return sanitized username
     */
    public static String username(String username) {
        if (username == null) {
            return "null";
        }
        // Username should be alphanumeric with limited special chars
        // Remove anything that looks suspicious
        String sanitized = sanitize(username);
        // Limit username to reasonable length
        if (sanitized.length() > 100) {
            return sanitized.substring(0, 100) + TRUNCATED_SUFFIX;
        }
        return sanitized;
    }

    /**
     * Sanitizes an IP address for logging.
     *
     * @param ipAddress the IP address to sanitize
     * @return sanitized IP address
     */
    public static String ipAddress(String ipAddress) {
        if (ipAddress == null) {
            return "unknown";
        }
        // IP addresses should be numeric with dots/colons (IPv4/IPv6)
        // Remove anything else
        String sanitized = ipAddress.replaceAll("[^0-9a-fA-F.:,]", "_");
        // Limit to reasonable length (IPv6 max is ~45 chars)
        if (sanitized.length() > 100) {
            return sanitized.substring(0, 100) + TRUNCATED_SUFFIX;
        }
        return sanitized;
    }

    /**
     * Sanitizes a filename for logging.
     *
     * @param filename the filename to sanitize
     * @return sanitized filename
     */
    public static String filename(String filename) {
        if (filename == null) {
            return "null";
        }
        String sanitized = sanitize(filename);
        // Limit filename to reasonable length
        if (sanitized.length() > 255) {
            return sanitized.substring(0, 255) + TRUNCATED_SUFFIX;
        }
        return sanitized;
    }
}
