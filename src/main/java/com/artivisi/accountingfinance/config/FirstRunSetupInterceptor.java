package com.artivisi.accountingfinance.config;

import com.artivisi.accountingfinance.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * When the users table is empty, redirect any browser request to /setup.
 * Static assets, error pages, API, and the setup endpoint itself are excluded
 * via WebMvcConfig path patterns.
 *
 * Once any user exists, this interceptor is a no-op (single count() query).
 */
@Component
@RequiredArgsConstructor
public class FirstRunSetupInterceptor implements HandlerInterceptor {

    private final UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
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
