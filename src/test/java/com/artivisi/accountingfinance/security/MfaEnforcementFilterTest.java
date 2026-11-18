package com.artivisi.accountingfinance.security;

import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The filter is the enforcement point, so it has to react to whatever
 * Authentication the chain established, including a session restored by
 * remember-me rather than a fresh login.
 */
@DisplayName("MFA enforcement filter")
class MfaEnforcementFilterTest {

    private static final String USERNAME = "budi";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final MfaService mfaService = mock(MfaService.class);
    private final User user = new User();

    private MfaEnforcementFilter filter;

    @BeforeEach
    void setUp() {
        user.setUsername(USERNAME);
        filter = new MfaEnforcementFilter(userRepository, mfaService);
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(USERNAME, "n/a", List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("an authenticated session with MFA enabled is sent to the verification page")
    void redirectsToVerification() throws Exception {
        when(mfaService.isEnabled()).thenReturn(true);
        when(mfaService.mustVerify(user)).thenReturn(true);

        MockHttpServletResponse response = doFilter("/dashboard");

        assertThat(response.getRedirectedUrl()).isEqualTo("/mfa-verify");
    }

    @Test
    @DisplayName("the same request passes once the session recorded a successful verification")
    void passesVerifiedSession() throws Exception {
        when(mfaService.isEnabled()).thenReturn(true);
        when(mfaService.mustVerify(user)).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(MfaEnforcementFilter.VERIFIED_USER_SESSION_ATTRIBUTE, USERNAME);

        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getRedirectedUrl()).isNull();
    }

    @Test
    @DisplayName("a role that requires MFA is sent to enrolment")
    void redirectsToEnrollment() throws Exception {
        when(mfaService.isEnabled()).thenReturn(true);
        when(mfaService.mustVerify(user)).thenReturn(false);
        when(mfaService.mustEnroll(user)).thenReturn(true);

        MockHttpServletResponse response = doFilter("/dashboard");

        assertThat(response.getRedirectedUrl()).isEqualTo("/settings/mfa?required=true");
    }

    @Test
    @DisplayName("the verification, settings and static paths stay reachable")
    void allowsTheEnrollmentAndVerificationPaths() throws Exception {
        when(mfaService.isEnabled()).thenReturn(true);
        when(mfaService.mustVerify(user)).thenReturn(true);
        when(mfaService.mustEnroll(user)).thenReturn(true);

        for (String path : List.of("/mfa-verify", "/settings/mfa", "/css/app.css", "/api/transactions", "/logout")) {
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(new MockHttpServletRequest("GET", path), new MockHttpServletResponse(), chain);
            assertThat(chain.getRequest()).as("path %s must pass", path).isNotNull();
        }
    }

    @Test
    @DisplayName("an HTMX request is redirected with HX-Redirect instead of a 302")
    void htmxRequestGetsRedirectHeader() throws Exception {
        when(mfaService.isEnabled()).thenReturn(true);
        when(mfaService.mustVerify(user)).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/transactions");
        request.addHeader(MfaEnforcementFilter.HX_REQUEST_HEADER, "true");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(MfaEnforcementFilter.HX_REDIRECT_HEADER)).isEqualTo("/mfa-verify");
        assertThat(response.getRedirectedUrl()).isNull();
    }

    @Test
    @DisplayName("the company logo can be read but not replaced before verification")
    void logoUploadIsNotAllowedBeforeVerification() throws Exception {
        when(mfaService.isEnabled()).thenReturn(true);
        when(mfaService.mustVerify(user)).thenReturn(true);

        MockFilterChain readChain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/settings/company/logo"),
                new MockHttpServletResponse(), readChain);
        assertThat(readChain.getRequest()).as("the waiting page renders the logo").isNotNull();

        MockHttpServletResponse uploadResponse = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST", "/settings/company/logo"),
                uploadResponse, new MockFilterChain());
        assertThat(uploadResponse.getRedirectedUrl()).isEqualTo("/mfa-verify");
    }

    @Test
    @DisplayName("nothing is enforced when MFA is switched off for the deployment")
    void doesNothingWhenDisabled() throws Exception {
        when(mfaService.isEnabled()).thenReturn(false);

        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/dashboard"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getRedirectedUrl()).isNull();
    }

    private MockHttpServletResponse doFilter(String path) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", path), response, new MockFilterChain());
        return response;
    }
}
