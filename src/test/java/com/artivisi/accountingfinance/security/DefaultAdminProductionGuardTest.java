package com.artivisi.accountingfinance.security;

import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultAdminProductionGuardTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    private static MockEnvironment environment(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }

    private DefaultAdminProductionGuard guard(MockEnvironment environment) {
        return guard(environment, false);
    }

    private DefaultAdminProductionGuard guard(MockEnvironment environment, boolean setupEnabled) {
        return new DefaultAdminProductionGuard(environment, userRepository, passwordEncoder, setupEnabled);
    }

    private static User admin(UUID id, String passwordHash) {
        User user = new User();
        user.setId(id);
        user.setUsername(DefaultAdminProductionGuard.DEFAULT_ADMIN_USERNAME);
        user.setPassword(passwordHash);
        return user;
    }

    @Test
    void rejectsSeededAdminByStableIdInProduction() {
        UUID id = DefaultAdminProductionGuard.SEEDED_ADMIN_ID;
        when(userRepository.findByUsername("admin"))
                .thenReturn(Optional.of(admin(id, "{bcrypt}changed")));

        var error = assertThrows(IllegalStateException.class,
                () -> guard(environment("production")).validate());

        assertTrue(error.getMessage().contains("Default admin/admin"));
    }

    @Test
    void rejectsSeededAdminEvenWhenSetupIsEnabled() {
        UUID id = DefaultAdminProductionGuard.SEEDED_ADMIN_ID;
        when(userRepository.findByUsername("admin"))
                .thenReturn(Optional.of(admin(id, "{bcrypt}changed")));

        var error = assertThrows(IllegalStateException.class,
                () -> guard(environment("production"), true).validate());

        assertTrue(error.getMessage().contains("Default admin/admin"));
    }

    @Test
    void rejectsAdminWhosePasswordMatchesDefaultInProduction() {
        UUID id = UUID.randomUUID();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin(id, "$2a$10$whatever")));
        when(passwordEncoder.matches("admin", "$2a$10$whatever")).thenReturn(true);

        var error = assertThrows(IllegalStateException.class,
                () -> guard(environment("production")).validate());

        assertTrue(error.getMessage().contains("Default admin/admin"));
    }

    @Test
    void allowsProductionWhenConfiguredAdminExists() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(userRepository.count()).thenReturn(1L);

        assertDoesNotThrow(() -> guard(environment("production")).validate());
    }

    @Test
    void allowsAdminWithNonDefaultPasswordInProduction() {
        UUID id = UUID.randomUUID();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin(id, "$2a$10$unique")));
        when(passwordEncoder.matches("admin", "$2a$10$unique")).thenReturn(false);
        when(userRepository.count()).thenReturn(1L);

        assertDoesNotThrow(() -> guard(environment("production")).validate());
    }

    @Test
    void rejectsEmptyUserTableWhenSetupIsDisabled() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(userRepository.count()).thenReturn(0L);

        var error = assertThrows(IllegalStateException.class,
                () -> guard(environment("production"), false).validate());

        assertTrue(error.getMessage().contains("No users exist"));
    }

    @Test
    void allowsEmptyUserTableWhenSetupIsExplicitlyEnabled() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(userRepository.count()).thenReturn(0L);

        assertDoesNotThrow(() -> guard(environment("production"), true).validate());
    }

    @Test
    void skipsCheckOutsideProduction() {
        assertDoesNotThrow(() -> guard(environment("test")).validate());

        verify(userRepository, never()).findByUsername("admin");
        verify(userRepository, never()).count();
    }
}
