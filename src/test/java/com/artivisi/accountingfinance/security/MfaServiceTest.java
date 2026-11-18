package com.artivisi.accountingfinance.security;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.repository.UserMfaRecoveryCodeRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.service.SecurityAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * The second factor is optional per user, mandatory only for the configured
 * roles, single use per code, rate limited, and recoverable both by the user and
 * by an administrator.
 */
@SpringBootTest(properties = {
        "app.mfa.enabled=true",
        "app.mfa.required-roles=ADMIN,OWNER",
        "app.mfa.max-attempts=3",
        "app.encryption.key=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="
})
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("TOTP enrolment and verification")
class MfaServiceTest {

    @Autowired
    private MfaService mfaService;

    @Autowired
    private MfaAttemptService attemptService;

    @Autowired
    private TotpService totpService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserMfaRecoveryCodeRepository recoveryCodeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User admin;
    private User staff;

    @BeforeEach
    void setUp() {
        attemptService.clear();
        admin = createUser(Role.ADMIN);
        staff = createUser(Role.STAFF);
    }

    @Test
    @DisplayName("the requirement follows the configured roles, not every user")
    void requirementFollowsRoles() {
        assertThat(mfaService.isRequiredFor(admin)).isTrue();
        assertThat(mfaService.isRequiredFor(staff)).isFalse();
        assertThat(mfaService.mustEnroll(admin)).isTrue();
        assertThat(mfaService.mustEnroll(staff)).isFalse();
    }

    @Test
    @DisplayName("enrolment stores the server-generated secret and returns one-time recovery codes")
    void enrolmentStoresSecretAndRecoveryCodes() {
        String secret = mfaService.startEnrollment(staff);
        long step = totpService.currentStep();

        List<String> recoveryCodes = mfaService.completeEnrollment(
                staff, secret, totpService.generateCode(secret, step), "test");

        User saved = userRepository.findById(staff.getId()).orElseThrow();
        assertThat(saved.getMfaEnabled()).isTrue();
        assertThat(saved.getMfaSecret()).isEqualTo(secret);
        assertThat(saved.getMfaLastUsedStep()).isEqualTo(step);
        assertThat(recoveryCodes).hasSize(10).doesNotHaveDuplicates();
        assertThat(mfaService.remainingRecoveryCodes(saved)).isEqualTo(10);
    }

    @Test
    @DisplayName("enrolment is refused when the confirmation code does not match the secret")
    void enrolmentRejectsWrongCode() {
        String secret = mfaService.startEnrollment(staff);

        assertThatThrownBy(() -> mfaService.completeEnrollment(staff, secret, "000000", "test"))
                .isInstanceOf(IllegalArgumentException.class);

        User saved = userRepository.findById(staff.getId()).orElseThrow();
        assertThat(saved.getMfaEnabled()).isFalse();
        assertThat(saved.getMfaSecret()).isNull();
    }

    @Test
    @DisplayName("the enrolment code and any accepted code cannot be replayed")
    void codeCannotBeReplayed() {
        long enrollmentStep = totpService.currentStep();
        String secret = mfaService.startEnrollment(staff);
        mfaService.completeEnrollment(staff, secret,
                totpService.generateCode(secret, enrollmentStep), staff.getUsername());

        String enrollmentCode = totpService.generateCode(secret, enrollmentStep);
        assertThat(mfaService.verify(staff, enrollmentCode)).isEqualTo(MfaService.VerifyOutcome.REPLAYED);

        String nextCode = totpService.generateCode(secret, enrollmentStep + 1);
        assertThat(mfaService.verify(staff, nextCode)).isEqualTo(MfaService.VerifyOutcome.OK);
        assertThat(mfaService.verify(staff, nextCode)).isEqualTo(MfaService.VerifyOutcome.REPLAYED);
        assertThat(mfaService.verify(staff, "999999")).isEqualTo(MfaService.VerifyOutcome.INVALID);
    }

    @Test
    @DisplayName("repeated failures lock the account inside the window")
    void repeatedFailuresLock() {
        enrolledSecret(staff);

        for (int i = 0; i < 3; i++) {
            assertThat(mfaService.verify(staff, "000000")).isEqualTo(MfaService.VerifyOutcome.INVALID);
        }

        assertThat(mfaService.verify(staff, "000000")).isEqualTo(MfaService.VerifyOutcome.LOCKED);
        assertThat(mfaService.verifyRecoveryCode(staff, "AAAA-BBBB-CCCC-DDDD")).isFalse();
    }

    @Test
    @DisplayName("a recovery code works once and is stored only as a hash")
    void recoveryCodeIsSingleUse() {
        String secret = mfaService.startEnrollment(staff);
        List<String> codes = mfaService.completeEnrollment(
                staff, secret, totpService.generateCode(secret, totpService.currentStep()), "test");

        assertThat(mfaService.verifyRecoveryCode(staff, codes.get(0))).isTrue();
        assertThat(mfaService.verifyRecoveryCode(staff, codes.get(0))).isFalse();
        assertThat(mfaService.verifyRecoveryCode(staff, codes.get(1))).isTrue();
        assertThat(mfaService.remainingRecoveryCodes(staff)).isEqualTo(8);

        String storedHash = recoveryCodeRepository.findByUser(staff).getFirst().getCodeHash();
        assertThat(storedHash).hasSize(64).doesNotContain(codes.get(0));
    }

    @Test
    @DisplayName("an administrator reset clears the secret, the flag and every recovery code")
    void adminResetClearsEverything() {
        enrolledSecret(staff);

        mfaService.adminReset(staff, "admin");

        User saved = userRepository.findById(staff.getId()).orElseThrow();
        assertThat(saved.getMfaEnabled()).isFalse();
        assertThat(saved.getMfaSecret()).isNull();
        assertThat(saved.getMfaLastUsedStep()).isNull();
        assertThat(recoveryCodeRepository.findByUser(saved)).isEmpty();
    }

    @Test
    @DisplayName("a user whose role requires MFA cannot switch it off")
    void requiredRoleCannotDisable() {
        String secret = enrolledSecret(admin);

        assertThatThrownBy(() -> mfaService.disable(admin, admin.getUsername()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN");

        assertThat(userRepository.findById(admin.getId()).orElseThrow().getMfaEnabled()).isTrue();
        assertThat(secret).isNotBlank();
    }

    @Test
    @DisplayName("an optional user can switch MFA off after confirming a fresh code")
    void optionalUserCanDisable() {
        String secret = enrolledSecret(staff);
        String code = totpService.generateCode(secret, totpService.currentStep() + 1);

        assertThat(mfaService.verify(staff, code)).isEqualTo(MfaService.VerifyOutcome.OK);
        mfaService.disable(staff, staff.getUsername());

        assertThat(userRepository.findById(staff.getId()).orElseThrow().getMfaEnabled()).isFalse();
    }

    @Test
    @DisplayName("regenerating recovery codes invalidates the previous list")
    void regeneratingInvalidatesOldCodes() {
        String secret = mfaService.startEnrollment(staff);
        List<String> first = mfaService.completeEnrollment(
                staff, secret, totpService.generateCode(secret, totpService.currentStep()), "test");
        String nextCode = totpService.generateCode(secret, totpService.currentStep() + 1);

        assertThat(mfaService.verify(staff, nextCode)).isEqualTo(MfaService.VerifyOutcome.OK);
        List<String> second = mfaService.regenerateRecoveryCodes(staff, "test");

        assertThat(second).hasSize(10).doesNotContainAnyElementsOf(first);
        assertThat(mfaService.verifyRecoveryCode(staff, first.get(0))).isFalse();
        assertThat(mfaService.verifyRecoveryCode(staff, second.get(0))).isTrue();
    }

    @Test
    @DisplayName("an already enrolled user cannot be re-enrolled and have the secret swapped")
    void reEnrollmentIsRefused() {
        String enrolled = enrolledSecret(staff);
        String attackerSecret = totpService.generateSecret();

        assertThatThrownBy(() -> mfaService.startEnrollment(staff))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sudah aktif");
        assertThatThrownBy(() -> mfaService.completeEnrollment(staff, attackerSecret,
                totpService.generateCode(attackerSecret, totpService.currentStep()), "attacker"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sudah aktif");

        User saved = userRepository.findById(staff.getId()).orElseThrow();
        assertThat(saved.getMfaSecret()).isEqualTo(enrolled);
        assertThat(mfaService.remainingRecoveryCodes(saved)).isEqualTo(10);
    }

    @Test
    @DisplayName("enrolment is refused when the deployment has no encryption key")
    void enrolmentRequiresEncryptionKey() {
        MfaService withoutKey = new MfaService(totpService, attemptService, userRepository,
                recoveryCodeRepository, mock(SecurityAuditService.class), true, "ADMIN", " ");

        assertThatThrownBy(() -> withoutKey.startEnrollment(staff))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_ENCRYPTION_KEY");

        String secret = totpService.generateSecret();
        assertThatThrownBy(() -> withoutKey.completeEnrollment(staff, secret,
                totpService.generateCode(secret, totpService.currentStep()), "test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_ENCRYPTION_KEY");
    }

    @Test
    @DisplayName("a misspelled role in app.mfa.required-roles stops startup instead of dropping the requirement")
    void unknownRoleFailsFast() {
        assertThatThrownBy(() -> new MfaService(totpService, attemptService, userRepository,
                recoveryCodeRepository, mock(SecurityAuditService.class), true, "ADMIN,ADMN", "key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMN");
    }

    private String enrolledSecret(User user) {
        String secret = mfaService.startEnrollment(user);
        mfaService.completeEnrollment(user, secret, totpService.generateCode(secret, totpService.currentStep()),
                user.getUsername());
        return secret;
    }

    private User createUser(Role role) {
        User user = new User();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        user.setUsername("mfa-" + role.name().toLowerCase() + "-" + suffix);
        user.setFullName("MFA Test " + suffix);
        user.setPassword(passwordEncoder.encode("Rahasia123!"));
        user.setActive(true);
        user.addRole(role, "test");
        return userRepository.save(user);
    }
}
