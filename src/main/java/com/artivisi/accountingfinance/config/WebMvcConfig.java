package com.artivisi.accountingfinance.config;

import com.artivisi.accountingfinance.security.CspNonceFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.EnumSet;

/**
 * Web MVC configuration for the application.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final CspNonceInterceptor cspNonceInterceptor;
    private final ThemeInterceptor themeInterceptor;
    private final ThemeConfig themeConfig;
    private final FirstRunSetupInterceptor firstRunSetupInterceptor;

    public WebMvcConfig(CspNonceInterceptor cspNonceInterceptor, ThemeInterceptor themeInterceptor,
                        ThemeConfig themeConfig, FirstRunSetupInterceptor firstRunSetupInterceptor) {
        this.cspNonceInterceptor = cspNonceInterceptor;
        this.themeInterceptor = themeInterceptor;
        this.themeConfig = themeConfig;
        this.firstRunSetupInterceptor = firstRunSetupInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Redirect to /setup when no users exist (first-run wizard)
        registry.addInterceptor(firstRunSetupInterceptor)
                .excludePathPatterns(
                        "/setup/**",
                        "/css/**", "/js/**", "/img/**", "/themes/**", "/webjars/**",
                        "/favicon.svg",
                        "/api/**",
                        "/actuator/**",
                        "/error",
                        "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
                );
        // Add CSP nonce to all Thymeleaf templates
        registry.addInterceptor(cspNonceInterceptor);
        // Add theme config to all Thymeleaf templates
        registry.addInterceptor(themeInterceptor);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve /themes/** from external filesystem directory (priority),
        // falling back to classpath for development defaults
        String dir = themeConfig.getDir();
        if (dir != null && !dir.isBlank()) {
            String filePath = dir.endsWith("/") ? dir : dir + "/";
            registry.addResourceHandler("/themes/**")
                    .addResourceLocations("file:" + filePath)
                    .addResourceLocations("classpath:/static/themes/");
        }
    }

    /**
     * Register CspNonceFilter to run for ALL dispatcher types including ERROR.
     * This ensures CSP headers are set even for error responses that bypass
     * the normal Spring Security filter chain (CWE-693 fix).
     */
    @Bean
    public FilterRegistrationBean<CspNonceFilter> cspNonceFilterRegistration(CspNonceFilter cspNonceFilter) {
        FilterRegistrationBean<CspNonceFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(cspNonceFilter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        // Run for all dispatcher types: REQUEST, ERROR, FORWARD, INCLUDE, ASYNC
        registration.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
        return registration;
    }
}
