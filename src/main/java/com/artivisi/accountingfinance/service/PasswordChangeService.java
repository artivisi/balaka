package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.AuditEventType;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.security.LogSanitizer;
import com.artivisi.accountingfinance.security.PasswordValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Every path that sets a password: the user's own change, an administrator setting it, and a reset.
 * All of them apply the same policy, stamp {@link User#getCredentialsChangedAt()} (which invalidates
 * web sessions authenticated earlier) and revoke the user's device tokens.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordChangeService {

    /**
     * Session attribute holding the instant the session was authenticated. A session whose value is
     * before the user's credentials_changed_at is invalidated by StaleSessionFilter.
     */
    public static final String SESSION_AUTHENTICATED_AT = PasswordChangeService.class.getName() + ".AUTHENTICATED_AT";

    static final String MSG_MISMATCH = "Password tidak cocok";
    static final String MSG_WRONG_CURRENT = "Password saat ini salah";
    static final String MSG_SAME_AS_CURRENT = "Password baru harus berbeda dari password saat ini";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidator passwordValidator;
    private final DeviceAuthService deviceAuthService;
    private final SecurityAuditService securityAuditService;

    /**
     * The authenticated user changes their own password. Clears the forced-change flag.
     *
     * @return the instant the credentials changed; the caller stores it on its own session so that
     *         session stays valid while every other one is invalidated
     */
    @Transactional
    public LocalDateTime changeOwnPassword(String username, String currentPassword,
                                           String newPassword, String confirmPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + username));
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            securityAuditService.logFor(AuditEventType.PASSWORD_CHANGED, username,
                    "Self-service change refused for user: " + username + " (current credential did not match)", false);
            throw new PasswordChangeException(List.of(MSG_WRONG_CURRENT));
        }
        validateNewPassword(user, newPassword, confirmPassword, true);

        Applied applied = apply(user, newPassword, false, username);
        securityAuditService.logFor(AuditEventType.PASSWORD_CHANGED, username,
                "Self-service change by user: " + username + "; device tokens revoked: " + applied.revokedTokens(), true);
        return applied.changedAt();
    }

    /**
     * An administrator sets another user's password. The user must change it at the next login.
     */
    @Transactional
    public void setByAdministrator(UUID userId, String newPassword, String confirmPassword, String administrator) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        if (user.getUsername().equals(administrator)) {
            throw new IllegalArgumentException("Use /account/password to change your own password");
        }
        validateNewPassword(user, newPassword, confirmPassword, false);

        Applied applied = apply(user, newPassword, true, administrator);
        securityAuditService.logFor(AuditEventType.PASSWORD_CHANGED, administrator,
                "Set by administrator: " + administrator + " for user: " + user.getUsername()
                        + " (id: " + userId + "); device tokens revoked: " + applied.revokedTokens(), true);
    }

    /**
     * Checks the new password against the confirmation and the policy, and (when the user chooses it)
     * that it differs from the current one. Mismatch is reported alone, as the form has to be re-typed.
     */
    void validateNewPassword(User user, String newPassword, String confirmPassword, boolean chosenByUser) {
        if (newPassword == null || !newPassword.equals(confirmPassword)) {
            throw new PasswordChangeException(List.of(MSG_MISMATCH));
        }
        List<String> errors = new ArrayList<>(passwordValidator.validate(newPassword).errors());
        if (errors.isEmpty() && chosenByUser && passwordEncoder.matches(newPassword, user.getPassword())) {
            errors.add(MSG_SAME_AS_CURRENT);
        }
        if (!errors.isEmpty()) {
            throw new PasswordChangeException(errors);
        }
    }

    /**
     * Stores the new password, sets or clears the forced-change flag, stamps the change and revokes the
     * user's device tokens. Flag transitions are audited with {@code actor} as the acting user.
     */
    Applied apply(User user, String newPassword, boolean requireChange, String actor) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        boolean wasRequired = Boolean.TRUE.equals(user.getPasswordChangeRequired());

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChangeRequired(requireChange);
        user.setCredentialsChangedAt(now);
        userRepository.save(user);
        int revoked = deviceAuthService.revokeAllTokens(user, actor);

        if (requireChange && !wasRequired) {
            securityAuditService.logFor(AuditEventType.PASSWORD_CHANGE_REQUIRED_SET, actor,
                    "Change at next login required for user: " + user.getUsername(), true);
        } else if (!requireChange && wasRequired) {
            securityAuditService.logFor(AuditEventType.PASSWORD_CHANGE_REQUIRED_CLEARED, actor,
                    "Change at next login no longer required for user: " + user.getUsername(), true);
        }
        log.info("Credentials changed for user {} by {}; {} device tokens revoked",
                LogSanitizer.username(user.getUsername()), LogSanitizer.username(actor), revoked);
        return new Applied(now, revoked);
    }

    record Applied(LocalDateTime changedAt, int revokedTokens) {
    }
}
