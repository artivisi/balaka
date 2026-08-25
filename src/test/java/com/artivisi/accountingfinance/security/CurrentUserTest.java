package com.artivisi.accountingfinance.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CurrentUser} exists because {@code getAuthentication()} returns null on any thread
 * that never passed through the security filter chain. That null is exactly what these tests
 * cover: it is unreachable from a functional test, which always runs inside a request.
 */
@DisplayName("CurrentUser - actor resolution")
class CurrentUserTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a", List.of()));
    }

    @Test
    @DisplayName("name() returns the authenticated principal")
    void nameReturnsPrincipal() {
        authenticateAs("endy");
        assertThat(CurrentUser.name()).isEqualTo("endy");
    }

    @Test
    @DisplayName("name() throws when the thread has no authentication")
    void nameThrowsWithoutAuthentication() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(CurrentUser::name)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nameOrSystem");
    }

    @Test
    @DisplayName("nameOrSystem() returns the authenticated principal when there is one")
    void nameOrSystemPrefersPrincipal() {
        authenticateAs("endy");
        assertThat(CurrentUser.nameOrSystem()).isEqualTo("endy");
    }

    @Test
    @DisplayName("nameOrSystem() falls back to the system actor on an unattended thread")
    void nameOrSystemFallsBackToSystem() {
        SecurityContextHolder.clearContext();

        // The scheduler path: MonthlyJournalScheduler -> AmortizationBatchService ->
        // JournalEntryService.getCurrentUsername() used to NPE here, and the scheduler's
        // catch-all logged it as a batch failure, so amortization never posted.
        assertThat(CurrentUser.nameOrSystem()).isEqualTo(CurrentUser.SYSTEM);
    }
}
