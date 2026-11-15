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
import org.springframework.mock.web.MockHttpSession;
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
 * Integration tests for MfaController.
 * Covers self-service enrolment and management of the second factor.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("MfaController Integration Tests")
class MfaControllerTest {

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

    private User staffUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        mfaAttemptService.clear();

        // Create test users
        staffUser = createUser(Role.STAFF);
        adminUser = createUser(Role.ADMIN);
    }

    private User createUser(Role role) {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername("mfa-test-" + suffix);
        user.setFullName("MFA Test " + suffix);
        user.setEmail("mfa-test-" + suffix + "@example.test");
        user.setPassword(passwordEncoder.encode("Rahasia123!"));
        user.setActive(true);
        user.addRole(role, "test");
        return userRepository.save(user);
    }

    @Nested
    @DisplayName("MFA Page (/settings/mfa)")
    class MfaPageTests {

        @Test
        @DisplayName("Should render MFA page when MFA is disabled globally")
        @WithUserDetails("mfa-test-staff")
        void shouldRenderMfaPageWhenDisabled() throws Exception {
            mockMvc.perform(get("/settings/mfa"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("settings/mfa"))
                    .andExpect(model().attributeExists("username", "mfaEnabled", "mfaAvailable", "mfaRequired"));
        }

        @Test
        @DisplayName("Should show enrollment form when MFA is enabled and user not enrolled")
        @WithUserDetails("mfa-test-staff")
        void shouldShowEnrollmentFormWhenNotEnrolled() throws Exception {
            mfaService.startEnrollment(staffUser); // This enables MFA globally

            mockMvc.perform(get("/settings/mfa").with(user(staffUser.getUsername())))
                    .andExpect(status().isOk())
                    .andExpect(view().name("settings/mfa"))
                    .andExpect(model().attributeExists("secret", "otpAuthUri", "digits", "period"));
        }
    }

    @Nested
    @DisplayName("Enable MFA (/settings/mfa/enable)")
    class EnableMfaTests {

        @Test
        @DisplayName("Should reject when no pending secret in session")
        @WithUserDetails("mfa-test-staff")
        void shouldRejectWhenNoPendingSecret() throws Exception {
            mockMvc.perform(post("/settings/mfa/enable")
                            .param("code", "123456")
                            .with(user(staffUser.getUsername()))
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection());
        }

        @Test
        @DisplayName("Should reject invalid code")
        @WithUserDetails("mfa-test-staff")
        void shouldRejectInvalidCode() throws Exception {
            String secret = mfaService.startEnrollment(staffUser);

            MockHttpSession session = new MockHttpSession();
            session.setAttribute("MFA_PENDING_SECRET", secret);

            mockMvc.perform(post("/settings/mfa/enable")
                            .param("code", "000000")
                            .session(session)
                            .with(user(staffUser.getUsername()))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("settings/mfa"))
                    .andExpect(model().attributeExists("errorMessage"));
        }
    }

    @Nested
    @DisplayName("Disable MFA (/settings/mfa/disable)")
    class DisableMfaTests {

        @Test
        @DisplayName("Should reject invalid code when disabling")
        @WithUserDetails("mfa-test-staff")
        void shouldRejectInvalidCodeWhenDisabling() throws Exception {
            String secret = totpService.generateSecret();
            staffUser.setMfaSecret(secret);
            staffUser.setMfaEnabled(true);
            userRepository.save(staffUser);

            mockMvc.perform(post("/settings/mfa/disable")
                            .param("code", "000000")
                            .with(user(staffUser.getUsername()))
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection());
        }
    }

    @Nested
    @DisplayName("Regenerate Recovery Codes (/settings/mfa/recovery-codes)")
    class RecoveryCodesTests {

        @Test
        @DisplayName("Should reject invalid code when regenerating recovery codes")
        @WithUserDetails("mfa-test-staff")
        void shouldRejectInvalidCodeWhenRegenerating() throws Exception {
            String secret = totpService.generateSecret();
            staffUser.setMfaSecret(secret);
            staffUser.setMfaEnabled(true);
            userRepository.save(staffUser);

            mockMvc.perform(post("/settings/mfa/recovery-codes")
                            .param("code", "000000")
                            .with(user(staffUser.getUsername()))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("settings/mfa"))
                    .andExpect(model().attributeExists("errorMessage"));
        }
    }
}