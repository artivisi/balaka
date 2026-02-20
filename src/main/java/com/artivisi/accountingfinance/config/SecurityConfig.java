package com.artivisi.accountingfinance.config;

import com.artivisi.accountingfinance.security.BearerTokenAuthenticationFilter;
import com.artivisi.accountingfinance.security.CspNonceHeaderWriter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Value("${transaction.api.require-auth:true}")
    private boolean requireApiAuth;

    @Bean
    @SuppressWarnings({"java:S112", "java:S1130"}) // Spring Security API requires throws Exception
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            UserDetailsService userDetailsService,
            BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter) throws Exception {
        http
            .userDetailsService(userDetailsService)
            // Add Bearer token authentication filter before username/password filter
            .addFilterBefore(bearerTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/css/**", "/js/**", "/img/**", "/themes/**", "/webjars/**").permitAll()
                    .requestMatchers("/login", "/error").permitAll()
                    .requestMatchers("/api/telegram/webhook").permitAll()
                    // Static API capabilities descriptor (unauthenticated)
                    .requestMatchers("/api/capabilities.json").permitAll()
                    // Device flow endpoints (unauthenticated)
                    .requestMatchers("/api/device/**").permitAll()
                    .requestMatchers("/device/**").permitAll();

                // API endpoints require Bearer token authentication (handled by filter)
                // No need for special handling here - filter sets authentication

                auth.anyRequest().authenticated();
            })
            // CSRF protection is disabled only for API endpoints which use token-based auth
            // or webhook endpoints (Telegram). This is safe because:
            // 1. API endpoints require Bearer token or webhook secret token authentication
            // 2. Browser-based forms still have CSRF protection enabled
            // Reviewed: This configuration is intentional for REST API compatibility
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/*/api/**", "/api/**")
            )
            // Disable CORS processing - this is a same-origin web application
            // When CORS is disabled, Spring Security doesn't apply CORS filter
            // and the browser's same-origin policy handles security
            .cors(cors -> cors.disable())
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard", true)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

