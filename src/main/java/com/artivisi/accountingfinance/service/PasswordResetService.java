package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.config.PasswordResetProperties;
import com.artivisi.accountingfinance.entity.PasswordResetToken;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.AuditEventType;
import com.artivisi.accountingfinance.repository.PasswordResetTokenRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.security.LogSanitizer;
import com.artivisi.accountingfinance.security.LoginAttemptService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Password reset by email (issue #61): a single-use, time-limited link, stored only as its SHA-256.
 * Requested from the login page by email address, or sent by an administrator from the user page.
 *
 * <p>The login-page request is answered the same way whether or not an account matches; the email is
 * sent on another thread after the token is committed, so the response time does not depend on it
 * either.
 */
@Service
@Slf4j
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;

    private final PasswordResetProperties properties;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private JavaMailSender mailSender;
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordChangeService passwordChangeService;
    private final SecurityAuditService securityAuditService;
    private final LoginAttemptService loginAttemptService;
    private final TaskExecutor taskExecutor;
    private final Environment environment;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Deque<LocalDateTime>> requestsByIp = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<LocalDateTime>> requestsByAccount = new ConcurrentHashMap<>();

    @SuppressWarnings("java:S107") // collaborators of one flow; splitting would only move the wiring
    public PasswordResetService(PasswordResetProperties properties,
                                ObjectProvider<JavaMailSender> mailSender,
                                UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                PasswordChangeService passwordChangeService,
                                SecurityAuditService securityAuditService,
                                LoginAttemptService loginAttemptService,
                                @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
                                Environment environment) {
        this.properties = properties;
        this.mailSenderProvider = mailSender;
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordChangeService = passwordChangeService;
        this.securityAuditService = securityAuditService;
        this.loginAttemptService = loginAttemptService;
        this.taskExecutor = taskExecutor;
        this.environment = environment;
    }

    /** Fails startup when reset is enabled without the settings it needs. */
    @PostConstruct
    void requireConfigurationWhenEnabled() {
        mailSender = mailSenderProvider.getIfAvailable();
        if (properties.isEnabled()) {
            requireConfiguration();
        }
    }

    private void requireConfiguration() {
        if (mailSender == null) {
            throw new IllegalStateException("app.password-reset.enabled=true but no mail sender is configured: "
                    + "set spring.mail.host (SPRING_MAIL_HOST) and the other spring.mail.* properties");
        }
        boolean smtpAuth = environment.getRequiredProperty("spring.mail.properties.mail.smtp.auth", Boolean.class);
        boolean hasUsername = !environment.getProperty("spring.mail.username", "").isBlank();
        boolean hasPassword = !environment.getProperty("spring.mail.password", "").isBlank();
        if (smtpAuth && !(hasUsername && hasPassword)) {
            throw new IllegalStateException("SMTP authentication is on (APP_SMTP_AUTH=true) but spring.mail.username "
                    + "or spring.mail.password is empty");
        }
        if (!smtpAuth && (hasUsername || hasPassword)) {
            throw new IllegalStateException("SMTP authentication is off (APP_SMTP_AUTH=false, e.g. Workspace SMTP relay "
                    + "by IP) but spring.mail.username or spring.mail.password is set; remove them or turn authentication on");
        }
        if (properties.getFrom() == null || properties.getFrom().isBlank()) {
            throw new IllegalStateException("app.password-reset.enabled=true but app.password-reset.from "
                    + "(APP_PASSWORD_RESET_FROM) is empty");
        }
        String baseUrl = properties.getBaseUrl();
        URI uri = baseUrl == null || baseUrl.isBlank() ? null : URI.create(baseUrl);
        if (uri == null || !uri.isAbsolute() || uri.getHost() == null
                || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalStateException("app.password-reset.enabled=true but app.password-reset.base-url "
                    + "(APP_BASE_URL) is not an absolute http(s) URL without query: '" + baseUrl + "'");
        }
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public long tokenValidityMinutes() {
        return properties.getTokenValidity().toMinutes();
    }

    /**
     * Login-page request. Sends a reset link when an active account has this address; otherwise does
     * nothing visible. The caller answers identically in both cases.
     *
     * @throws PasswordResetUnavailableException when reset is not configured
     * @throws PasswordResetRateLimitedException when this client IP exceeded its quota
     */
    @Transactional
    public void requestReset(String email, String clientIp) {
        requireEnabled();
        if (!admit(requestsByIp, clientIp == null ? "unknown" : clientIp, properties.getMaxRequestsPerIp())) {
            securityAuditService.logFor(AuditEventType.PASSWORD_RESET_REQUESTED, "anonymous",
                    "Refused: request quota of the client address exceeded", false);
            throw new PasswordResetRateLimitedException();
        }
        String address = email == null ? "" : email.trim();
        Optional<User> user = address.isEmpty() ? Optional.empty()
                : userRepository.findByEmailIgnoreCase(address).filter(u -> Boolean.TRUE.equals(u.getActive()));
        if (user.isEmpty()) {
            securityAuditService.logFor(AuditEventType.PASSWORD_RESET_REQUESTED, "anonymous",
                    "No active account has the submitted address; nothing sent", false);
            return;
        }
        if (!admit(requestsByAccount, user.get().getId(), properties.getMaxRequestsPerAccount())) {
            securityAuditService.logFor(AuditEventType.PASSWORD_RESET_REQUESTED, user.get().getUsername(),
                    "Not sent to user: " + user.get().getUsername() + " (per-account quota exceeded)", false);
            return;
        }
        issue(user.get(), PasswordResetToken.REQUESTED_BY_SELF, user.get().getUsername());
    }

    /**
     * An administrator sends the reset email instead of typing a password.
     */
    @Transactional
    public void sendByAdministrator(UUID userId, String administrator) {
        requireEnabled();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new IllegalArgumentException("Pengguna tidak aktif: " + user.getUsername());
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalArgumentException("Pengguna " + user.getUsername() + " tidak memiliki alamat email");
        }
        issue(user, administrator, administrator);
    }

    /**
     * State of a token as presented in a reset link.
     */
    @Transactional(readOnly = true)
    public TokenState check(String rawToken) {
        return find(rawToken).map(t -> state(t, now())).orElse(TokenState.UNKNOWN);
    }

    /**
     * Sets the new password from a valid token. Ends the user's sessions and device tokens, clears a
     * login lockout, and consumes the token.
     *
     * @return the username, for the login page
     * @throws PasswordResetTokenException when the token is unknown, used, superseded or expired
     * @throws PasswordChangeException when the new password is refused; the token stays usable
     */
    @Transactional
    public String completeReset(String rawToken, String newPassword, String confirmPassword) {
        requireEnabled();
        LocalDateTime now = now();
        PasswordResetToken token = find(rawToken).orElseThrow(() -> new PasswordResetTokenException(TokenState.UNKNOWN));
        TokenState state = state(token, now);
        if (state != TokenState.VALID) {
            securityAuditService.logFor(AuditEventType.PASSWORD_RESET_COMPLETED, token.getUser().getUsername(),
                    "Refused for user: " + token.getUser().getUsername() + " (link " + state.name().toLowerCase() + ")", false);
            throw new PasswordResetTokenException(state);
        }
        User user = token.getUser();
        passwordChangeService.validateNewPassword(user, newPassword, confirmPassword, true);

        token.setUsedAt(now);
        tokenRepository.save(token);
        PasswordChangeService.Applied applied = passwordChangeService.apply(user, newPassword, false, user.getUsername());
        tokenRepository.invalidateOpenTokens(user, now);
        loginAttemptService.loginSucceeded(user.getUsername());
        securityAuditService.logFor(AuditEventType.PASSWORD_RESET_COMPLETED, user.getUsername(),
                "Completed for user: " + user.getUsername() + "; device tokens revoked: " + applied.revokedTokens(), true);
        return user.getUsername();
    }

    private void issue(User user, String requestedBy, String auditActor) {
        LocalDateTime now = now();
        tokenRepository.invalidateOpenTokens(user, now);

        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(now.plus(properties.getTokenValidity()));
        token.setRequestedBy(requestedBy);
        tokenRepository.save(token);

        securityAuditService.logFor(AuditEventType.PASSWORD_RESET_REQUESTED, auditActor,
                "Reset link issued for user: " + user.getUsername() + " (requested by: " + requestedBy + ")", true);

        String to = user.getEmail();
        String username = user.getUsername();
        String link = properties.getBaseUrl().replaceAll("/+$", "") + "/reset-password?token=" + rawToken;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                taskExecutor.execute(() -> send(to, username, link));
            }
        });
    }

    private void send(String to, String username, String link) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getFrom());
        message.setTo(to);
        message.setSubject("Reset password akun " + username);
        message.setText(String.join(System.lineSeparator(),
                "Ada permintaan reset password untuk akun " + username + ".",
                "",
                "Buka tautan berikut dalam " + tokenValidityMinutes() + " menit untuk membuat password baru:",
                link,
                "",
                "Tautan hanya dapat dipakai sekali. Abaikan email ini jika Anda tidak meminta reset password;",
                "password Anda tidak berubah.",
                ""));
        try {
            mailSender.send(message);
            log.info("Password reset email sent for user {}", LogSanitizer.username(username));
        } catch (MailException e) {
            log.error("Password reset email for user {} could not be sent", LogSanitizer.username(username), e);
            securityAuditService.logFor(AuditEventType.PASSWORD_RESET_REQUESTED, username,
                    "Reset email for user: " + username + " could not be sent: " + e.getClass().getSimpleName(), false);
        }
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new PasswordResetUnavailableException();
        }
    }

    private Optional<PasswordResetToken> find(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 100) {
            return Optional.empty();
        }
        return tokenRepository.findByTokenHash(hash(rawToken));
    }

    private static TokenState state(PasswordResetToken token, LocalDateTime now) {
        if (token.isUsed()) {
            return TokenState.USED;
        }
        if (token.isInvalidated()) {
            return TokenState.SUPERSEDED;
        }
        if (token.isExpired(now)) {
            return TokenState.EXPIRED;
        }
        if (!Boolean.TRUE.equals(token.getUser().getActive())) {
            return TokenState.UNKNOWN;
        }
        return TokenState.VALID;
    }

    /** Sliding window: admits the request when fewer than {@code max} were admitted within the window. */
    private <K> boolean admit(Map<K, Deque<LocalDateTime>> requests, K key, int max) {
        LocalDateTime now = now();
        LocalDateTime windowStart = now.minus(properties.getRateWindow());
        Deque<LocalDateTime> times = requests.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst().isBefore(windowStart)) {
                times.pollFirst();
            }
            if (times.size() >= max) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }

    private static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public enum TokenState {
        VALID, UNKNOWN, USED, SUPERSEDED, EXPIRED
    }
}
