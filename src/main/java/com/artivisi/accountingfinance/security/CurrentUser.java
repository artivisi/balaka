package com.artivisi.accountingfinance.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Resolves the actor to stamp on audit fields. Utility class with static accessors,
 * mirroring {@link Permission}.
 *
 * <p>{@code SecurityContextHolder.getContext().getAuthentication()} returns null on any
 * thread that never passed through the security filter chain — a {@code @Scheduled}
 * batch job, an {@code ApplicationReadyEvent} hook, a plain {@code @Async} task.
 * Dereferencing it there throws NPE, which is how scheduled amortization posting used to
 * fail silently inside the scheduler's catch-all. Callers therefore pick one of the two
 * accessors deliberately: {@link #name()} for request-bound code, {@link #nameOrSystem()}
 * for code a scheduler can reach.
 */
public final class CurrentUser {

    /** Actor recorded for unattended execution. Matches USER_SYSTEM in the MVC controllers. */
    public static final String SYSTEM = "system";

    private CurrentUser() {
    }

    /**
     * Name of the authenticated principal.
     *
     * @throws IllegalStateException when the calling thread has no authentication, meaning
     *         the operation ran outside a request and the caller should be using
     *         {@link #nameOrSystem()} instead
     */
    public static String name() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new IllegalStateException("Tidak ada pengguna terautentikasi pada thread ini; "
                    + "operasi yang terikat request dijalankan di luar request. "
                    + "Pemanggil tak berpenghuni harus memakai CurrentUser.nameOrSystem().");
        }
        return authentication.getName();
    }

    /**
     * Name of the authenticated principal, or {@link #SYSTEM} when the calling thread has
     * none. For code paths a scheduler or batch job can reach.
     */
    public static String nameOrSystem() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? SYSTEM : authentication.getName();
    }
}
