package com.artivisi.accountingfinance.config;

import com.artivisi.accountingfinance.security.BearerTokenAuthenticationFilter;
import com.artivisi.accountingfinance.security.CspNonceHeaderWriter;
import com.artivisi.accountingfinance.service.SecurityAuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Value("${transaction.api.require-auth:true}")
    private boolean requireApiAuth;

    @Value("${app.remember-me.key}")
    private String rememberMeKey;

    @Bean
    @SuppressWarnings({"java:S112", "java:S1130"}) // Spring Security API requires throws Exception
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            UserDetailsService userDetailsService,
            BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter,
            SecurityAuditService securityAuditService) throws Exception {
        http
            .userDetailsService(userDetailsService)
            // Add Bearer token authentication filter before username/password filter
            .addFilterBefore(bearerTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/css/**", "/js/**", "/img/**", "/themes/**", "/webjars/**").permitAll()
                    .requestMatchers("/login", "/error").permitAll()
                    .requestMatchers("/setup", "/setup/**").permitAll()
                    .requestMatchers("/api/telegram/webhook").permitAll()
                    // OpenAPI spec and Swagger UI (unauthenticated)
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    // Device flow endpoints (unauthenticated)
                    .requestMatchers("/api/device/**").permitAll()
                    // OAuth2 client_credentials token endpoint (authenticates via client secret)
                    .requestMatchers("/api/oauth/token").permitAll()
                    // Container/platform health probes. Only the health group is
                    // exposed and show-details is never, so this returns status only.
                    .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                    .requestMatchers("/device/**").permitAll()
                    .requestMatchers("/settings/company/logo").permitAll();  // Allow logo on login page

                // API endpoints require Bearer token authentication (handled by filter)
                // No need for special handling here - filter sets authentication

                auth.anyRequest().authenticated();
            })
            // CSRF is waived only for stateless requests that cannot ride on a session cookie:
            // /api/** calls carrying a Bearer token (BearerTokenAuthenticationFilter authenticates
            // them by token alone, ignoring any session), and the unauthenticated device-flow,
            // client_credentials and Telegram webhook endpoints. Cookie-session requests to
            // /api/** and to the web JSON endpoints under /*/api/** require the CSRF token.
            .csrf(csrf -> csrf
                .ignoringRequestMatchers(
                    SecurityConfig::isBearerApiRequest,
                    PathPatternRequestMatcher.withDefaults().matcher("/api/device/**"),
                    PathPatternRequestMatcher.withDefaults().matcher("/api/oauth/token"),
                    PathPatternRequestMatcher.withDefaults().matcher("/api/telegram/webhook"))
            )
            // Disable CORS processing - this is a same-origin web app
            // When CORS is disabled, Spring Security doesn't apply CORS filter
            // and the browser's same-origin policy handles security
            .cors(cors -> cors.disable())
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard", false)
                .failureHandler(loginFailureHandler(securityAuditService))
                .permitAll()
            )
            .rememberMe(remember -> remember
                .key(rememberMeKey)
                .tokenValiditySeconds(604800) // 7 days
                .rememberMeParameter("remember-me")
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .deleteCookies("remember-me")
                .permitAll()
            )
            // Return 401 for unauthenticated API requests instead of redirecting to login
            .exceptionHandling(exceptions -> exceptions
                .defaultAuthenticationEntryPointFor(
                    (request, response, authException) -> {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\":\"unauthorized\",\"message\":\"Authentication required\"}");
                    },
                    request -> request.getRequestURI().startsWith("/api/")
                )
                // CsrfFilter runs before any /api request is authenticated: a POST without a Bearer
                // token and without a session is unauthenticated (401), not forbidden.
                .defaultAccessDeniedHandlerFor(
                    (request, response, accessDeniedException) -> {
                        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                        boolean anonymous = auth == null || auth instanceof AnonymousAuthenticationToken;
                        response.setStatus(anonymous ? HttpServletResponse.SC_UNAUTHORIZED : HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json");
                        response.getWriter().write(anonymous
                                ? "{\"error\":\"unauthorized\",\"message\":\"Authentication required\"}"
                                : "{\"error\":\"forbidden\",\"message\":\"Access denied\"}");
                    },
                    request -> request.getRequestURI().startsWith("/api/")
                )
            )
            // Security headers configuration
            .headers(headers -> headers
                // Content Security Policy with dynamic nonce (replaces unsafe-inline/unsafe-eval)
                // Custom implementation required because Spring Security doesn't support dynamic nonces
                .addHeaderWriter(new CspNonceHeaderWriter())
                // Prevent clickjacking - deny embedding in frames
                .frameOptions(frame -> frame.deny())
                // Prevent MIME type sniffing
                .contentTypeOptions(Customizer.withDefaults())
                // HTTP Strict Transport Security - force HTTPS
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000) // 1 year
                )
                // Referrer policy - limit information sent in Referer header
                .referrerPolicy(referrer -> referrer
                    .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                )
                // Permissions policy - disable unnecessary browser features
                .permissionsPolicyHeader(permissions -> permissions
                    .policy("geolocation=(), microphone=(), camera=(), payment=()")
                )
            );

        return http.build();
    }

    /**
     * A login refused because LoginAttemptService locked the account fails with LockedException
     * (wrapped in InternalAuthenticationServiceException by DaoAuthenticationProvider). The
     * HttpSecurity-local ProviderManager rethrows it without publishing an authentication event,
     * so the blocked attempt is audited here.
     */
    private static AuthenticationFailureHandler loginFailureHandler(SecurityAuditService securityAuditService) {
        SimpleUrlAuthenticationFailureHandler delegate = new SimpleUrlAuthenticationFailureHandler("/login?error");
        return (request, response, exception) -> {
            if (exception instanceof LockedException || exception.getCause() instanceof LockedException) {
                securityAuditService.logLogin(request.getParameter("username"), false, "Account locked - login blocked");
            }
            delegate.onAuthenticationFailure(request, response, exception);
        };
    }

    private static boolean isBearerApiRequest(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return request.getRequestURI().startsWith("/api/")
                && authorization != null
                && authorization.startsWith("Bearer ");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
