package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.security.MfaAttemptService;
import com.artivisi.accountingfinance.security.MfaService;
import com.artivisi.accountingfinance.security.TotpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Integration tests for MfaVerifyController.
 * Covers the second step of login: TOTP code or recovery code verification.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
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

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        mfaAttemptService.clear();

        // Create test user with MFA enabled
        adminUser = createEnrolledUser(Role.ADMIN);
    }

    private User createEnrolledUser(Role role) {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername("mfa-verify-" + suffix);
        user.setFullName("MFA Verify " + suffix);
        user.setEmail("mfa-verify-" + suffix + "@example.test");
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
        @DisplayName("Should redirect to dashboard when user not authenticated")
        void shouldRedirectWhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/mfa-verify"))
                    .andExpect(status().is3xxRedirection());
        }

        @Test
        @DisplayName("Should redirect to dashboard when MFA not required for user")
        @WithUserDetails("mfa-verify-admin")
        void shouldRedirectWhenMfaNotRequired() throws Exception {
            mockMvc.perform(get("/mfa-verify").with(user(adminUser.getUsername())))
                    .andExpect(status().is3xxRedirection());
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
        void shouldRejectWhenNotAuthenticated() throws Exception {
            mockMvc.perform(post("/mfa-verify")
                            .param("code", "123456")
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection());
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