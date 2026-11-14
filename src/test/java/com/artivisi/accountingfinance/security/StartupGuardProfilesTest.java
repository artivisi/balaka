package com.artivisi.accountingfinance.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Startup guard profile selection")
class StartupGuardProfilesTest {

    private MockEnvironment env(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }

    @Test
    @DisplayName("no profile is a deployment, so the guards apply")
    void noProfileIsGuarded() {
        assertThat(StartupGuardProfiles.enforced(env())).isTrue();
    }

    @Test
    @DisplayName("the development profiles are the only ones that opt out")
    void developmentProfilesAreExempt() {
        assertThat(StartupGuardProfiles.enforced(env("dev"))).isFalse();
        assertThat(StartupGuardProfiles.enforced(env("test"))).isFalse();
        assertThat(StartupGuardProfiles.enforced(env("functional"))).isFalse();
    }

    @Test
    @DisplayName("bootstrap is exempt only together with its exit flag")
    void bootstrapNeedsTheExitFlag() {
        assertThat(StartupGuardProfiles.enforced(env("bootstrap"))).isTrue();

        MockEnvironment withFlag = env("bootstrap");
        withFlag.setProperty(StartupGuardProfiles.BOOTSTRAP_EXIT_FLAG, "true");
        assertThat(StartupGuardProfiles.enforced(withFlag)).isFalse();

        MockEnvironment falseFlag = env("bootstrap");
        falseFlag.setProperty(StartupGuardProfiles.BOOTSTRAP_EXIT_FLAG, "false");
        assertThat(StartupGuardProfiles.enforced(falseFlag)).isTrue();
    }

    @Test
    @DisplayName("bootstrap next to a dev profile stays unguarded for local runs")
    void bootstrapWithDevStaysExempt() {
        MockEnvironment environment = env("dev", "bootstrap");
        assertThat(StartupGuardProfiles.enforced(environment)).isFalse();
    }
}
