package com.artivisi.accountingfinance.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * One-shot startup mode used by deployment automation. It lets Flyway run and
 * JPA validate the schema, then closes the context and exits without serving
 * requests, so the seeded admin can be replaced before the production profile
 * starts (and its {@code DefaultAdminProductionGuard} runs).
 *
 * Enabled only with {@code --app.bootstrap.exit-after-migrate=true}. It refuses
 * to run with the production profile active: the whole point is to perform the
 * admin swap while the production guard is not yet enforcing startup.
 */
@Component
@ConditionalOnProperty(name = "app.bootstrap.exit-after-migrate", havingValue = "true")
@Order(Ordered.LOWEST_PRECEDENCE)
@Slf4j
public class BootstrapMigrationRunner implements ApplicationRunner {

    private final ConfigurableApplicationContext applicationContext;
    private final Environment environment;

    public BootstrapMigrationRunner(ConfigurableApplicationContext applicationContext,
                                    Environment environment) {
        this.applicationContext = applicationContext;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        validateProfile();
        int exitCode = SpringApplication.exit(applicationContext, () -> 0);
        log.info("Bootstrap migration run complete; exit code {}", exitCode);
    }

    void validateProfile() {
        if (environment.acceptsProfiles(Profiles.of("production"))) {
            throw new IllegalStateException(
                    "Bootstrap migration mode must not run with the production profile");
        }
    }
}
