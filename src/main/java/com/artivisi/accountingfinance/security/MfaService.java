package com.artivisi.accountingfinance.security;

import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.entity.UserMfaRecoveryCode;
import com.artivisi.accountingfinance.enums.AuditEventType;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.repository.UserMfaRecoveryCodeRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.service.SecurityAuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * TOTP enrolment, verification and recovery.
 *
 * <p>Enrolment is opt-in per user and mandatory only for the roles listed in
 * {@code app.mfa.required-roles}. The secret is generated here and held in the
 * session between the setup page and the confirmation POST, never taken from a
 * request parameter: a client that supplies its own secret would choose a key it
 * already knows and could skip the second factor.
 */
@Service
@Slf4j
@Transactional
public class MfaService {

    public enum VerifyOutcome {
        /** The code was accepted. */
        OK,
        /** The code does not match any allowed time step. */
        INVALID,
        /** The code matched a step that was already used. */
        REPLAYED,
        /** Too many failed attempts inside the window. */
        LOCKED,
        /** The user has no confirmed secret. */
        NOT_ENROLLED
    }

    private static final int RECOVERY_CODE_COUNT = 10;
    /** Same zone the deployment runs in (-Duser.timezone=Asia/Jakarta). */
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Jakarta");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final TotpService totpService;
    private final MfaAttemptService attemptService;
    private final UserRepository userRepository;
    private final UserMfaRecoveryCodeRepository recoveryCodeRepository;
    private final SecurityAuditService securityAuditService;

    private final boolean enabled;
    private final Set<Role> requiredRoles;

    public MfaService(TotpService totpService,
                      MfaAttemptService attemptService,
                      UserRepository userRepository,
                      UserMfaRecoveryCodeRepository recoveryCodeRepository,
                      SecurityAuditService securityAuditService,
                      @Value("${app.mfa.enabled:true}") boolean enabled,
                      @Value("${app.mfa.required-roles:ADMIN,OWNER}") String requiredRoles) {
        this.totpService = totpService;
        this.attemptService = attemptService;
        this.userRepository = userRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.securityAuditService = securityAuditService;
        this.enabled = enabled;
        this.requiredRoles = parseRoles(requiredRoles);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Set<Role> getRequiredRoles() {
        return requiredRoles;
    }

    /** True when this user's role set makes a second factor mandatory. */
    public boolean isRequiredFor(User user) {
        if (!enabled || user == null) {
            return false;
        }
        return user.getRoles().stream().anyMatch(requiredRoles::contains);
    }

    /** True when an authenticated session still has to pass a second factor. */
    public boolean mustVerify(User user) {
        return enabled && user != null && Boolean.TRUE.equals(user.getMfaEnabled())
                && user.getMfaSecret() != null;
    }

    /** True when the user has to enrol before continuing. */
    public boolean mustEnroll(User user) {
        return isRequiredFor(user) && !Boolean.TRUE.equals(user.getMfaEnabled());
    }

    /**
     * Step 1: a candidate secret for the setup page. The caller keeps it in the
     * session; nothing is written to the user row until a code proves the app
     * holds it.
     */
    public String startEnrollment(User user) {
        requireEnabled();
        if (Boolean.TRUE.equals(user.getMfaEnabled())) {
            throw new IllegalStateException("MFA is already enabled for this user");
        }
        return totpService.generateSecret();
    }

    /**
     * Step 2: confirm the pending secret with a code and return the recovery
     * codes in plaintext, the only time they are readable.
     */
    public List<String> completeEnrollment(User user, String pendingSecret, String code, String actor) {
        requireEnabled();
        long step = totpService.matchingStep(pendingSecret, code);
        if (step < 0) {
            securityAuditService.log(AuditEventType.MFA_ENABLE_FAILED,
                    "MFA enrolment rejected for user " + user.getUsername() + ": invalid code");
            throw new IllegalArgumentException("Kode OTP tidak valid. Periksa jam perangkat dan coba lagi.");
        }

        user.setMfaSecret(pendingSecret);
        user.setMfaEnabled(true);
        user.setMfaLastUsedStep(step);
        userRepository.save(user);

        List<String> codes = replaceRecoveryCodes(user);
        securityAuditService.log(AuditEventType.MFA_ENABLED,
                "MFA enabled for user " + user.getUsername() + " by " + actor);
        return codes;
    }

    /**
     * Verify a TOTP code for an authenticated user. A code is accepted at most
     * once: its time step is persisted and any step at or before it is refused,
     * so a code captured from the screen cannot be replayed.
     */
    public VerifyOutcome verify(User user, String code) {
        if (!enabled) {
            return VerifyOutcome.OK;
        }
        if (user == null || !Boolean.TRUE.equals(user.getMfaEnabled()) || user.getMfaSecret() == null) {
            return VerifyOutcome.NOT_ENROLLED;
        }
        if (attemptService.isBlocked(user.getUsername())) {
            return VerifyOutcome.LOCKED;
        }

        long step = totpService.matchingStep(user.getMfaSecret(), code);
        if (step < 0) {
            recordFailure(user, "invalid code");
            return VerifyOutcome.INVALID;
        }
        if (user.getMfaLastUsedStep() != null && step <= user.getMfaLastUsedStep()) {
            recordFailure(user, "code already used");
            return VerifyOutcome.REPLAYED;
        }

        user.setMfaLastUsedStep(step);
        userRepository.save(user);
        attemptService.succeeded(user.getUsername());
        securityAuditService.log(AuditEventType.MFA_VERIFY_SUCCESS,
                "MFA code accepted for user " + user.getUsername());
        return VerifyOutcome.OK;
    }

    /**
     * Consume a recovery code. Each code works once; the row keeps the hash and
     * the moment it was used, never the code itself.
     */
    public boolean verifyRecoveryCode(User user, String code) {
        if (!enabled || user == null || code == null || code.isBlank()) {
            return false;
        }
        if (attemptService.isBlocked(user.getUsername())) {
            return false;
        }

        String hash = hashRecoveryCode(code);
        var match = recoveryCodeRepository.findByUserAndCodeHashAndUsedAtIsNull(user, hash);
        if (match.isEmpty()) {
            recordFailure(user, "invalid recovery code");
            return false;
        }

        UserMfaRecoveryCode recoveryCode = match.get();
        recoveryCode.setUsedAt(LocalDateTime.now(APP_ZONE));
        recoveryCodeRepository.save(recoveryCode);
        attemptService.succeeded(user.getUsername());
        securityAuditService.log(AuditEventType.MFA_RECOVERY_USED,
                "MFA recovery code used by " + user.getUsername()
                        + " (" + remainingRecoveryCodes(user) + " remaining)");
        return true;
    }

    public long remainingRecoveryCodes(User user) {
        return recoveryCodeRepository.findByUser(user).stream()
                .filter(code -> code.getUsedAt() == null)
                .count();
    }

    /** Replace the recovery codes; the caller shows the new list once. */
    public List<String> regenerateRecoveryCodes(User user, String actor) {
        if (!Boolean.TRUE.equals(user.getMfaEnabled())) {
            throw new IllegalStateException("MFA is not enabled for this user");
        }
        List<String> codes = replaceRecoveryCodes(user);
        securityAuditService.log(AuditEventType.MFA_RECOVERY_CODES_REGENERATED,
                "MFA recovery codes regenerated for " + user.getUsername() + " by " + actor);
        return codes;
    }

    /** The user turns their own second factor off; refused when a role requires it. */
    public void disable(User user, String actor) {
        if (isRequiredFor(user)) {
            throw new IllegalStateException(
                    "Role " + requiredRoleNames(user) + " mewajibkan MFA, jadi tidak bisa dinonaktifkan.");
        }
        clear(user);
        securityAuditService.log(AuditEventType.MFA_DISABLED,
                "MFA disabled for user " + user.getUsername() + " by " + actor);
    }

    /** An administrator clears a lost authenticator so the user can enrol again. */
    public void adminReset(User user, String actor) {
        clear(user);
        securityAuditService.log(AuditEventType.MFA_ADMIN_RESET,
                "MFA reset for user " + user.getUsername() + " by " + actor);
    }

    private void clear(User user) {
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        user.setMfaLastUsedStep(null);
        userRepository.save(user);
        recoveryCodeRepository.deleteByUser(user);
        attemptService.succeeded(user.getUsername());
    }

    private void recordFailure(User user, String reason) {
        attemptService.failed(user.getUsername());
        if (attemptService.isBlocked(user.getUsername())) {
            securityAuditService.log(AuditEventType.MFA_LOCKED,
                    "MFA verification locked for user " + user.getUsername() + " after repeated " + reason);
        } else {
            securityAuditService.log(AuditEventType.MFA_VERIFY_FAILED,
                    "MFA verification failed for " + user.getUsername() + ": " + reason);
        }
    }

    private List<String> replaceRecoveryCodes(User user) {
        recoveryCodeRepository.deleteByUser(user);
        List<String> plain = new ArrayList<>(RECOVERY_CODE_COUNT);
        List<UserMfaRecoveryCode> rows = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String code = randomRecoveryCode();
            plain.add(code);
            rows.add(new UserMfaRecoveryCode(user, hashRecoveryCode(code)));
        }
        recoveryCodeRepository.saveAll(rows);
        return plain;
    }

    private String randomRecoveryCode() {
        byte[] bytes = new byte[10];
        SECURE_RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(TotpService.base32Encode(bytes));
        while (sb.length() < 16) {
            sb.append('A');
        }
        String raw = sb.substring(0, 16);
        return raw.substring(0, 4) + "-" + raw.substring(4, 8) + "-"
                + raw.substring(8, 12) + "-" + raw.substring(12, 16);
    }

    private String hashRecoveryCode(String code) {
        String normalized = code.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new IllegalStateException("MFA is disabled on this deployment");
        }
    }

    private String requiredRoleNames(User user) {
        return user.getRoles().stream()
                .filter(requiredRoles::contains)
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }

    private static Set<Role> parseRoles(String configured) {
        if (configured == null || configured.isBlank()) {
            return EnumSet.noneOf(Role.class);
        }
        Set<Role> roles = EnumSet.noneOf(Role.class);
        Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .forEach(name -> {
                    try {
                        roles.add(Role.valueOf(name.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException _) {
                        log.warn("Ignoring unknown role '{}' in app.mfa.required-roles", name);
                    }
                });
        return roles;
    }
}
