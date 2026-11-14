package com.artivisi.accountingfinance.config;

import com.artivisi.accountingfinance.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FirstRunSetupInterceptorTest {

    private final UserRepository userRepository = mock(UserRepository.class);

    @Test
    void doesNotRedirectOrQueryUsersWhenSetupIsDisabled() throws Exception {
        FirstRunSetupInterceptor interceptor = new FirstRunSetupInterceptor(userRepository, false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));

        assertNull(response.getRedirectedUrl());
    }

    @Test
    void redirectsToSetupWhenEnabledAndUsersAreEmpty() throws Exception {
        when(userRepository.count()).thenReturn(0L);
        FirstRunSetupInterceptor interceptor = new FirstRunSetupInterceptor(userRepository, true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));

        assertEquals("/setup", response.getRedirectedUrl());
    }

    @Test
    void allowsRequestWhenEnabledAndAUserExists() throws Exception {
        when(userRepository.count()).thenReturn(1L);
        FirstRunSetupInterceptor interceptor = new FirstRunSetupInterceptor(userRepository, true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));

        assertNull(response.getRedirectedUrl());
    }

    @Test
    void leavesSetupEndpointReachableWhenEnabled() throws Exception {
        when(userRepository.count()).thenReturn(0L);
        FirstRunSetupInterceptor interceptor = new FirstRunSetupInterceptor(userRepository, true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/setup");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));

        assertNull(response.getRedirectedUrl());
    }
}
