package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.security.MfaAttemptService;
import com.artivisi.accountingfinance.security.CspNonceFilter;
import com.artivisi.accountingfinance.security.MfaService;
import com.artivisi.accountingfinance.security.TotpService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Integration tests for MfaVerifyController.
 * Covers the second step of login: TOTP code or recovery code verification.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
// The shared test profile turns MFA off so other suites are not blocked; these tests exercise it.
@TestPropertySource(properties = "app.mfa.enabled=true")
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("MfaVerifyController Integration Tests")
class MfaVerifyControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TotpService totpService;

    @Autowired
    private MfaService mfaService;

    @Autowired
    private MfaAttemptService mfaAttemptService;

    private MockMvc mockMvc;

    private User adminUser;
    private User staffUser;

    /**
     * Seeded once per class: @WithUserDetails resolves the principal before @BeforeEach
     * runs, so a user created per test method is never there when it is needed.
     */
    @BeforeAll
    void seedUsers() {
        adminUser = createEnrolledUser("mfa-verify-admin", Role.ADMIN);
        staffUser = createEnrolledUser("mfa-verify-staff", Role.STAFF);
        staffUser.setMfaEnabled(false);
        staffUser.setMfaSecret(null);
        staffUser = userRepository.save(staffUser);
    }

    @BeforeEach
    void setUp() {
        adminUser = userRepository.findByUsername("mfa-verify-admin").orElseThrow();
        staffUser = userRepository.findByUsername("mfa-verify-staff").orElseThrow();
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                // The servlet container registers CspNonceFilter ahead of Spring Security,
                // and the layout refuses to render a nonce-less script tag.
                .addFilters(webApplicationContext.getBean(CspNonceFilter.class))
                .apply(springSecurity())
                .build();
        mfaAttemptService.clear();
    }

    private User createEnrolledUser(String username, Role role) {
        User user = userRepository.findByUsername(username).orElseGet(User::new);
        user.setUsername(username);
        user.setFullName("MFA Verify " + role.name());
        user.setEmail(username + "@example.test");
        user.setPassword(passwordEncoder.encode("Rahasia123!"));
        user.setActive(true);
        user.addRole(role, "test");

        String secret = totpService.generateSecret();
        user.setMfaSecret(secret);
        user.setMfaEnabled(true);
        user.setMfaLastUsedStep(null);
        return userRepository.save(user);
    }

    @Nested
    @DisplayName("Verification Form (/mfa-verify)")
    class VerificationFormTests {

        @Test
        @DisplayName("Should reject the request when the user is not authenticated")
        void shouldRejectWhenNotAuthenticated() throws Exception {
            // The security chain answers 401 before the controller runs, which is what
            // production does for every page that is not the login form.
            mockMvc.perform(get("/mfa-verify"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should redirect to dashboard when MFA not required for user")
        @WithUserDetails("mfa-verify-staff")
        void shouldRedirectWhenMfaNotRequired() throws Exception {
            mockMvc.perform(get("/mfa-verify").with(user(staffUser.getUsername())))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/dashboard"));
        }

        @Test
        @DisplayName("Should show verification page when MFA required and not verified")
        @WithUserDetails("mfa-verify-admin")
        void shouldShowVerificationPageWhenRequired() throws Exception {
            mockMvc.perform(get("/mfa-verify").with(user(adminUser.getUsername())))
                    .andExpect(status().isOk())
                    .andExpect(view().name("mfa-verify"))
                    .andExpect(model().attributeExists("username"));
        }
    }

    @Nested
    @DisplayName("Verify Code (/mfa-verify POST)")
    class VerifyCodeTests {

        @Test
        @DisplayName("Should reject when not authenticated")
        void shouldRejectPostWhenNotAuthenticated() throws Exception {
            mockMvc.perform(post("/mfa-verify")
                            .param("code", "123456")
                            .with(csrf()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should reject invalid TOTP code")
        @WithUserDetails("mfa-verify-admin")
        void shouldRejectInvalidTotpCode() throws Exception {
            mockMvc.perform(post("/mfa-verify")
                            .param("code", "000000")
                            .with(user(adminUser.getUsername()))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("mfa-verify"))
                    .andExpect(model().attributeExists("errorMessage"));
        }

        @Test
        @DisplayName("Should reject invalid recovery code")
        @WithUserDetails("mfa-verify-admin")
        void shouldRejectInvalidRecoveryCode() throws Exception {
            mockMvc.perform(post("/mfa-verify")
                            .param("recoveryCode", "invalid")
                            .with(user(adminUser.getUsername()))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("mfa-verify"))
                    .andExpect(model().attributeExists("errorMessage"));
        }

        @Test
        @DisplayName("Should block after too many attempts")
        @WithUserDetails("mfa-verify-admin")
        void shouldBlockAfterTooManyAttempts() throws Exception {
            // Make 5 failed attempts (default max-attempts)
            for (int i = 0; i < 5; i++) {
                mockMvc.perform(post("/mfa-verify")
                                .param("code", "000000")
                                .with(user(adminUser.getUsername()))
                                .with(csrf()));
            }

            // 6th attempt should be blocked
            mockMvc.perform(post("/mfa-verify")
                            .param("code", "000000")
                            .with(user(adminUser.getUsername()))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("mfa-verify"))
                    .andExpect(model().attributeExists("errorMessage"));
        }
    }
}