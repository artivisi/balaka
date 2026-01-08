package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.SecurityAuditLog;
import com.artivisi.accountingfinance.enums.AuditEventType;
import com.artivisi.accountingfinance.repository.SecurityAuditLogRepository;
import com.artivisi.accountingfinance.security.LogSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * Service for logging security audit events.
 * Provides compliance with PCI-DSS Requirement 10 and GDPR Article 30.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityAuditService {

    private final SecurityAuditLogRepository auditLogRepository;

    // Pattern to mask sensitive data in details (use non-greedy and specific patterns to avoid ReDoS)
    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(password|token|secret|key|npwp|nik|bank_?number|account_?number|bpjs)([\"':=\\s]+)([^\"',\\s}]+)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Log a security event with current user context.
     */
    @Transactional
    public void log(AuditEventType eventType, String details) {
        logInternal(eventType, details, true);
    }

    /**
     * Log a security event with current user context and success flag.
     */
    @Transactional
    public void log(AuditEventType eventType, String details, boolean success) {
        logInternal(eventType, details, success);
    }

    /**
     * Log a security event asynchronously (for non-critical events).
     */
    @Async
    @Transactional
    public void logAsync(AuditEventType eventType, String details) {
        logInternal(eventType, details, true);
    }

    /**
     * Internal implementation for logging security events.
     * This avoids @Transactional self-invocation issues by having all public methods
     * delegate to this private method.
     */
    private void logInternal(AuditEventType eventType, String details, boolean success) {
        String username = getCurrentUsername();
        String ipAddress = getClientIpAddress();
        String userAgent = getUserAgent();

        SecurityAuditLog auditLog = SecurityAuditLog.of(eventType, username)
                .withIpAddress(ipAddress)
                .withUserAgent(userAgent)
                .withDetails(maskSensitiveData(details))
                .withSuccess(success);

        auditLogRepository.save(auditLog);

        // Also log to application logs (sanitize to prevent log injection)
        if (success) {
            log.info("AUDIT: {} by {} from {} - {}",
                    eventType, LogSanitizer.username(username),
                    LogSanitizer.ipAddress(ipAddress), LogSanitizer.sanitize(details));
        } else {
            log.warn("AUDIT: {} by {} from {} - FAILED - {}",
                    eventType, LogSanitizer.username(username),
                    LogSanitizer.ipAddress(ipAddress), LogSanitizer.sanitize(details));
        }
    }

    /**
     * Log a login event (success or failure).
     */
    @Transactional
    public void logLogin(String username, boolean success, String details) {
        String ipAddress = getClientIpAddress();
        String userAgent = getUserAgent();

        AuditEventType eventType = success ? AuditEventType.LOGIN_SUCCESS : AuditEventType.LOGIN_FAILURE;

        SecurityAuditLog auditLog = SecurityAuditLog.of(eventType, username)
                .withIpAddress(ipAddress)
                .withUserAgent(userAgent)
                .withDetails(details)
                .withSuccess(success);

        auditLogRepository.save(auditLog);

        // Sanitize to prevent log injection
        if (success) {
            log.info("AUDIT: LOGIN_SUCCESS for {} from {}",
                    LogSanitizer.username(username), LogSanitizer.ipAddress(ipAddress));
        } else {
            log.warn("AUDIT: LOGIN_FAILURE for {} from {} - {}",
                    LogSanitizer.username(username), LogSanitizer.ipAddress(ipAddress),
                    LogSanitizer.sanitize(details));
        }
    }

    /**
     * Search audit logs with filters.
     */
    @Transactional(readOnly = true)
    public Page<SecurityAuditLog> search(
            AuditEventType eventType,
            String username,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable) {
        String eventTypeStr = eventType != null ? eventType.name() : null;
        return auditLogRepository.search(eventTypeStr, username, startDate, endDate, pageable);
    }

    /**
     * Get audit logs for the current user.
     */
    @Transactional(readOnly = true)
    public Page<SecurityAuditLog> getMyAuditLogs(Pageable pageable) {
        String username = getCurrentUsername();
        return auditLogRepository.findByUsername(username, pageable);
    }

    /**
     * Get current authenticated username.
     */
    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return "anonymous";
    }

    /**
     * Get client IP address from request.
     */
    private String getClientIpAddress() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                // Check X-Forwarded-For header for proxy/load balancer scenarios
                String xForwardedFor = request.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                    return xForwardedFor.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            log.debug("Could not get client IP: {}", e.getMessage());
        }
        return "unknown";
    }

    /**
     * Get user agent from request.
     */
    private String getUserAgent() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                return attrs.getRequest().getHeader("User-Agent");
            }
        } catch (Exception e) {
            log.debug("Could not get user agent: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Mask sensitive data in audit log details.
     */
    private String maskSensitiveData(String details) {
        if (details == null) {
            return null;
        }
        return SENSITIVE_PATTERN.matcher(details).replaceAll("$1$2****");
    }
}
