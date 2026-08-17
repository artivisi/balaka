package com.artivisi.accountingfinance;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    // Default: a throwaway postgres:18-alpine wired in via @ServiceConnection.
    // Demo data loaders pass -Ddemo.external-db=true to skip the container and run
    // against an external Postgres (set via spring.datasource.*) so the populated DB
    // can be captured with pg_dump. matchIfMissing keeps every other test unchanged.
    // Memory/CPU limits are applied to every container by ContainerResourceDefaults,
    // registered via META-INF/services — see that class for why Apple Container needs
    // them. Setting them here would only cover Postgres, not Ryuk or the sshd helper.
    @Bean
    @ServiceConnection
    @ConditionalOnProperty(name = "demo.external-db", havingValue = "false", matchIfMissing = true)
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:18-alpine");
    }
}
