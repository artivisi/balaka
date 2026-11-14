package com.artivisi.accountingfinance.security;

import org.springframework.core.env.Environment;

import java.util.Set;

/**
 * Decides whether the production startup guards apply.
 *
 * <p>The guards enforce by default: no profile, or any profile other than an
 * explicit development profile, is treated as a deployment. That is the fail-closed
 * direction, because forgetting to pass {@code --spring.profiles.active=production}
 * must not silently disable them. Only the profiles that never serve real users are
 * exempt:
 *
 * <ul>
 *   <li>{@code dev}, {@code test}, {@code functional} - local development and the
 *       test suites;</li>
 *   <li>{@code bootstrap} - the one-shot migration run in the deployment playbook
 *       that replaces the seeded admin <em>before</em> the production profile starts
 *       ({@code --app.bootstrap.exit-after-migrate=true}, no web server).</li>
 * </ul>
 */
final class StartupGuardProfiles {

    static final String PRODUCTION = "production";
    static final String BOOTSTRAP = "bootstrap";
    static final String BOOTSTRAP_EXIT_FLAG = "app.bootstrap.exit-after-migrate";

    private static final Set<String> EXEMPT = Set.of("dev", "test", "functional");

    private StartupGuardProfiles() {
    }

    static boolean enforced(Environment environment) {
        Set<String> active = Set.of(environment.getActiveProfiles());
        for (String profile : active) {
            if (EXEMPT.contains(profile)) {
                return false;
            }
        }
        if (active.contains(BOOTSTRAP)) {
            // The bootstrap exemption belongs to the one-shot run that replaces the
            // seeded admin, and that run is identified by its exit flag. A bootstrap
            // profile without the flag would serve requests with the guards off, so
            // it is treated as a deployment instead of being trusted by name.
            return !environment.getProperty(BOOTSTRAP_EXIT_FLAG, Boolean.class, false);
        }
        return true;
    }
}
