package com.artivisi.accountingfinance.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LogSanitizer Tests")
class LogSanitizerTest {

    @Nested
    @DisplayName("sanitize() - General Input Sanitization")
    class SanitizeTests {

        @Test
        @DisplayName("Should return 'null' string for null input")
        void shouldReturnNullStringForNull() {
            assertThat(LogSanitizer.sanitize(null)).isEqualTo("null");
        }

        @Test
        @DisplayName("Should return normal strings unchanged")
        void shouldReturnNormalStringsUnchanged() {
            String input = "Normal log message";
            assertThat(LogSanitizer.sanitize(input)).isEqualTo(input);
        }

        @Test
        @DisplayName("Should replace newline characters with underscore")
        void shouldReplaceNewlines() {
            String input = "Line1\nLine2\nLine3";

            String sanitized = LogSanitizer.sanitize(input);

            assertThat(sanitized).isEqualTo("Line1_Line2_Line3").doesNotContain("\n");
        }

        @Test
        @DisplayName("Should replace carriage return with underscore")
        void shouldReplaceCarriageReturn() {
            String input = "Line1\rLine2";

            String sanitized = LogSanitizer.sanitize(input);

            assertThat(sanitized).isEqualTo("Line1_Line2").doesNotContain("\r");
        }

        @Test
        @DisplayName("Should replace CRLF with underscores")
        void shouldReplaceCrlf() {
            String input = "Line1\r\nLine2";

            String sanitized = LogSanitizer.sanitize(input);

            assertThat(sanitized).isEqualTo("Line1__Line2");
        }

        @Test
        @DisplayName("Should replace tab with space")
        void shouldReplaceTab() {
            String input = "Col1\tCol2\tCol3";

            String sanitized = LogSanitizer.sanitize(input);

            assertThat(sanitized).isEqualTo("Col1 Col2 Col3");
        }

        @Test
        @DisplayName("Should replace control characters with question mark")
        void shouldReplaceControlCharacters() {
            String input = "Test\u0000\u0007\u001fEnd";

            String sanitized = LogSanitizer.sanitize(input);

            assertThat(sanitized).isEqualTo("Test???End");
        }

        @Test
        @DisplayName("Should truncate long strings")
        void shouldTruncateLongStrings() {
            String input = "A".repeat(600);

            String sanitized = LogSanitizer.sanitize(input);

            assertThat(sanitized).hasSize(500 + "...[truncated]".length()).endsWith("...[truncated]");
        }

        @Test
        @DisplayName("Should not truncate strings at limit")
        void shouldNotTruncateAtLimit() {
            String input = "A".repeat(500);

            String sanitized = LogSanitizer.sanitize(input);

            assertThat(sanitized).hasSize(500).doesNotContain("[truncated]");
        }

        @Test
        @DisplayName("Should prevent log injection attacks")
        void shouldPreventLogInjection() {
            // Attacker tries to inject fake log entry
            String malicious = "user login\n2024-01-01 12:00:00 WARN Admin logged in";

            String sanitized = LogSanitizer.sanitize(malicious);

            assertThat(sanitized).doesNotContain("\n").isEqualTo("user login_2024-01-01 12:00:00 WARN Admin logged in");
        }
    }

    @Nested
    @DisplayName("username() - Username Sanitization")
    class UsernameTests {

        @Test
        @DisplayName("Should return 'null' string for null input")
        void shouldReturnNullStringForNull() {
            assertThat(LogSanitizer.username(null)).isEqualTo("null");
        }

        @Test
        @DisplayName("Should return normal usernames unchanged")
        void shouldReturnNormalUsernameUnchanged() {
            String username = "john.doe";
            assertThat(LogSanitizer.username(username)).isEqualTo(username);
        }

        @Test
        @DisplayName("Should sanitize username with injection attempt")
        void shouldSanitizeInjectionAttempt() {
            String malicious = "admin\nFake log entry";

            String sanitized = LogSanitizer.username(malicious);

            assertThat(sanitized).isEqualTo("admin_Fake log entry");
        }

        @Test
        @DisplayName("Should truncate long usernames to 100 chars")
        void shouldTruncateLongUsernames() {
            String longUsername = "user".repeat(50);

            String sanitized = LogSanitizer.username(longUsername);

            assertThat(sanitized).hasSize(100 + "...[truncated]".length()).endsWith("...[truncated]");
        }

        @ParameterizedTest
        @ValueSource(strings = {"admin", "user123", "john.doe", "test_user"})
        @DisplayName("Should handle various valid usernames")
        void shouldHandleValidUsernames(String username) {
            assertThat(LogSanitizer.username(username)).isEqualTo(username);
        }
    }

    @Nested
    @DisplayName("ipAddress() - IP Address Sanitization")
    class IpAddressTests {

        @Test
        @DisplayName("Should return 'unknown' for null input")
        void shouldReturnUnknownForNull() {
            assertThat(LogSanitizer.ipAddress(null)).isEqualTo("unknown");
        }

        @ParameterizedTest
        @ValueSource(strings = {"192.168.1.1", "10.0.0.1", "255.255.255.255"})
        @DisplayName("Should return valid IPv4 addresses unchanged")
        void shouldReturnValidIpv4Unchanged(String ip) {
            assertThat(LogSanitizer.ipAddress(ip)).isEqualTo(ip);
        }

        @ParameterizedTest
        @ValueSource(strings = {"::1", "fe80::1", "2001:0db8:85a3:0000:0000:8a2e:0370:7334"})
        @DisplayName("Should return valid IPv6 addresses unchanged")
        void shouldReturnValidIpv6Unchanged(String ip) {
            assertThat(LogSanitizer.ipAddress(ip)).isEqualTo(ip);
        }

        @Test
        @DisplayName("Should replace invalid characters with underscore")
        void shouldReplaceInvalidCharacters() {
            String malicious = "192.168.1.1\nFake entry";

            String sanitized = LogSanitizer.ipAddress(malicious);

            assertThat(sanitized).isEqualTo("192.168.1.1_Fa_e_e____");
        }

        @Test
        @DisplayName("Should truncate long IP strings")
        void shouldTruncateLongIpStrings() {
            String longIp = "192.168.1.".repeat(20);

            String sanitized = LogSanitizer.ipAddress(longIp);

            assertThat(sanitized).isEqualTo("192.168.1.".repeat(10) + "...[truncated]");
        }

        @Test
        @DisplayName("Should handle X-Forwarded-For format")
        void shouldHandleXForwardedFor() {
            String forwarded = "192.168.1.1,10.0.0.1";

            String sanitized = LogSanitizer.ipAddress(forwarded);

            // Comma is preserved for IP lists
            assertThat(sanitized).isEqualTo("192.168.1.1,10.0.0.1");
        }
    }

    @Nested
    @DisplayName("filename() - Filename Sanitization")
    class FilenameTests {

        @Test
        @DisplayName("Should return 'null' string for null input")
        void shouldReturnNullStringForNull() {
            assertThat(LogSanitizer.filename(null)).isEqualTo("null");
        }

        @ParameterizedTest
        @ValueSource(strings = {"document.pdf", "image.png", "report-2024.xlsx"})
        @DisplayName("Should return normal filenames unchanged")
        void shouldReturnNormalFilenamesUnchanged(String filename) {
            assertThat(LogSanitizer.filename(filename)).isEqualTo(filename);
        }

        @Test
        @DisplayName("Should sanitize filename with injection attempt")
        void shouldSanitizeInjectionAttempt() {
            String malicious = "file.pdf\nDeleted all files";

            String sanitized = LogSanitizer.filename(malicious);

            assertThat(sanitized).isEqualTo("file.pdf_Deleted all files");
        }

        @Test
        @DisplayName("Should truncate long filenames to 255 chars")
        void shouldTruncateLongFilenames() {
            String longFilename = "a".repeat(300) + ".pdf";

            String sanitized = LogSanitizer.filename(longFilename);

            assertThat(sanitized).isEqualTo("a".repeat(255) + "...[truncated]");
        }

        @Test
        @DisplayName("Should handle path traversal attempts")
        void shouldHandlePathTraversal() {
            String malicious = "../../../etc/passwd";

            String sanitized = LogSanitizer.filename(malicious);

            // The sanitize method doesn't validate path structure, just log safety
            assertThat(sanitized).isEqualTo("../../../etc/passwd");
        }
    }

    @Nested
    @DisplayName("Security Edge Cases")
    class SecurityEdgeCases {

        @Test
        @DisplayName("Should handle ANSI escape sequences")
        void shouldHandleAnsiEscapes() {
            String ansiColor = "\u001b[31mRed Text\u001b[0m";

            String sanitized = LogSanitizer.sanitize(ansiColor);

            assertThat(sanitized).isEqualTo("?[31mRed Text?[0m");
        }

        @Test
        @DisplayName("Should handle Unicode control characters")
        void shouldHandleUnicodeControl() {
            String unicode = "Test\u200BZero-Width\u200CText";

            // Zero-width characters are outside the ASCII control range and pass through
            String sanitized = LogSanitizer.sanitize(unicode);

            assertThat(sanitized).isEqualTo(unicode);
        }

        @Test
        @DisplayName("Should handle mixed attack vectors")
        void shouldHandleMixedAttacks() {
            String attack = "user\nINFO Fake log\rMore\ttabs\u0000null";

            String sanitized = LogSanitizer.sanitize(attack);

            assertThat(sanitized).isEqualTo("user_INFO Fake log_More tabs?null");
        }
    }
}
