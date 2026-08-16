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
    // Apple Container (the macOS dev runtime) gives every container its own VM with
    // a fixed reservation — 1 GB / 4 CPUs by default — instead of sharing one VM as
    // Docker Engine and OrbStack do. A full suite run holds ~17 Postgres containers
    // at once, which reserved 18 GB on a 16 GB machine and made the host swap until
    // Playwright navigations hit their 15s timeout. These caps keep the same
    // per-context isolation at ~1/4 the footprint and are ignored by Docker Engine
    // beyond the usual cgroup limits, so CI is unaffected.
    private static final long CONTAINER_MEMORY_BYTES = 512L * 1024 * 1024;
    // NanoCPUs is what `docker run --cpus` sets and what the runtime reads.
    // HostConfig.withCpuCount is the Windows-only field and is silently ignored here.
    private static final long CONTAINER_NANO_CPUS = 2L * 1_000_000_000L;

    @Bean
    @ServiceConnection
    @ConditionalOnProperty(name = "demo.external-db", havingValue = "false", matchIfMissing = true)
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:18-alpine")
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig()
                        .withMemory(CONTAINER_MEMORY_BYTES)
                        .withNanoCPUs(CONTAINER_NANO_CPUS));
    }
}
