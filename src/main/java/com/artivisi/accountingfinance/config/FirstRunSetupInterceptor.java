package com.artivisi.accountingfinance.config;

import com.artivisi.accountingfinance.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * When the users table is empty and the setup wizard is explicitly enabled,
 * redirect any browser request to /setup. Static assets, error pages, API, and
 * the setup endpoint itself are excluded via WebMvcConfig path patterns.
 *
 * When {@code app.setup.enabled=false} (the default) this interceptor is a
 * no-op, so an empty database can never expose an unauthenticated first-admin
 * claim path. Once any user exists, it is also a no-op (single count() query
 * on a small indexed table — negligible overhead).
 */
@Component
public class FirstRunSetupInterceptor implements HandlerInterceptor {

    private final UserRepository userRepository;
    private final boolean setupEnabled;

    public FirstRunSetupInterceptor(UserRepository userRepository,
                                    @Value("${app.setup.enabled:false}") boolean setupEnabled) {
        this.userRepository = userRepository;
        this.setupEnabled = setupEnabled;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!setupEnabled) {
            return true;
        }
        if (userRepository.count() > 0) {
            return true;
        }
        String path = request.getRequestURI();
        if (path.startsWith("/setup")) {
            return true;
        }
        response.sendRedirect(request.getContextPath() + "/setup");
        return false;
    }
}
