package com.artivisi.accountingfinance.config;

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
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, UserDetailsService userDetailsService) throws Exception {
        http
            .userDetailsService(userDetailsService)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/img/**", "/webjars/**").permitAll()
                .requestMatchers("/login", "/error").permitAll()
                .requestMatchers("/api/telegram/webhook").permitAll()
                .anyRequest().authenticated()
            )
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
            // Security headers configuration
            .headers(headers -> headers
                // Content Security Policy - controls allowed sources for scripts, styles, etc.
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives(
                        "default-src 'self'; " +
                        "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://cdn.jsdelivr.net https://unpkg.com; " +
                        "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://fonts.googleapis.com; " +
                        "font-src 'self' https://fonts.gstatic.com https://cdn.jsdelivr.net; " +
                        "img-src 'self' data: blob:; " +
                        "connect-src 'self'; " +
                        "frame-ancestors 'none'; " +
                        "form-action 'self'; " +
                        "base-uri 'self'"
                    )
                )
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
