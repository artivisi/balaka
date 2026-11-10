package com.artivisi.accountingfinance.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class BootstrapMigrationRunnerTest {

    private final ConfigurableApplicationContext applicationContext = mock(ConfigurableApplicationContext.class);

    private static MockEnvironment environment(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }

    @Test
    void refusesToRunWithProductionProfile() {
        BootstrapMigrationRunner runner =
                new BootstrapMigrationRunner(applicationContext, environment("production"));

        var error = assertThrows(IllegalStateException.class, runner::validateProfile);

        assertTrue(error.getMessage().contains("must not run with the production profile"));
    }

    @Test
    void allowsNonProductionProfiles() {
        BootstrapMigrationRunner runner =
                new BootstrapMigrationRunner(applicationContext, environment("bootstrap"));

        assertDoesNotThrow(runner::validateProfile);
    }
}
