package com.employeecare.api.auth.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PublicEndpointRateLimitFilterTest {

    private PublicEndpointRateLimitFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(
                Instant.parse("2026-11-09T15:00:00Z"),
                ZoneOffset.UTC
        );
        filter = new PublicEndpointRateLimitFilter(fixedClock);
        filterChain = mock(FilterChain.class);
    }

    @Test
    @DisplayName("POST /public/tickets/anonymous - Autorise 5 créations par minute puis bloque la 6e avec 429")
    void anonymousTicketCreation_shouldAllow5AndBlock6thRequest() throws Exception {
        for (int i = 1; i <= PublicEndpointRateLimitFilter.MAX_ANONYMOUS_CREATE_PER_WINDOW; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/public/tickets/anonymous");
            request.setContextPath("/api");
            request.setServletPath("/public/tickets/anonymous");
            request.setRemoteAddr("192.168.1.50");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        }

        MockHttpServletRequest blockedRequest = new MockHttpServletRequest("POST", "/api/public/tickets/anonymous");
        blockedRequest.setContextPath("/api");
        blockedRequest.setServletPath("/public/tickets/anonymous");
        blockedRequest.setRemoteAddr("192.168.1.50");
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();

        filter.doFilter(blockedRequest, blockedResponse, filterChain);

        assertThat(blockedResponse.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(blockedResponse.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
        assertThat(blockedResponse.getContentAsString()).contains("Trop de requêtes");
        verify(filterChain, times(5)).doFilter(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    @DisplayName("POST /auth/login - Autorise 20 tentatives par minute puis bloque la 21e avec 429")
    void loginEndpoint_shouldAllow20AndBlock21stRequest() throws Exception {
        for (int i = 1; i <= PublicEndpointRateLimitFilter.MAX_PUBLIC_AUTH_OR_TRACKING_PER_WINDOW; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setContextPath("/api");
            request.setServletPath("/auth/login");
            request.setRemoteAddr("10.0.0.15");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        }

        MockHttpServletRequest blockedRequest = new MockHttpServletRequest("POST", "/api/auth/login");
        blockedRequest.setContextPath("/api");
        blockedRequest.setServletPath("/auth/login");
        blockedRequest.setRemoteAddr("10.0.0.15");
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();

        filter.doFilter(blockedRequest, blockedResponse, filterChain);

        assertThat(blockedResponse.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    @DisplayName("Isolations par IP - Une IP bloquée n'impacte pas une autre adresse IP")
    void differentClientIps_shouldHaveIndependentCounters() throws Exception {
        for (int i = 0; i <= PublicEndpointRateLimitFilter.MAX_ANONYMOUS_CREATE_PER_WINDOW; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/public/tickets/anonymous");
            req.setServletPath("/public/tickets/anonymous");
            req.setRemoteAddr("192.168.1.10");
            filter.doFilter(req, new MockHttpServletResponse(), filterChain);
        }

        MockHttpServletRequest otherIpRequest = new MockHttpServletRequest("POST", "/api/public/tickets/anonymous");
        otherIpRequest.setServletPath("/public/tickets/anonymous");
        otherIpRequest.setRemoteAddr("192.168.1.99");
        MockHttpServletResponse otherIpResponse = new MockHttpServletResponse();

        filter.doFilter(otherIpRequest, otherIpResponse, filterChain);

        assertThat(otherIpResponse.getStatus()).isEqualTo(HttpStatus.OK.value());
    }
    @Test
    @DisplayName("En-tête X-Forwarded-For non fiable ne permet pas de contourner la limitation")
    void spoofedXForwardedForHeader_shouldNotBypassRateLimit() throws Exception {
        String clientIp = "192.168.1.77";

        for (int i = 1; i <= PublicEndpointRateLimitFilter.MAX_ANONYMOUS_CREATE_PER_WINDOW; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/public/tickets/anonymous");
            request.setContextPath("/api");
            request.setServletPath("/public/tickets/anonymous");
            request.setRemoteAddr(clientIp);
            request.addHeader("X-Forwarded-For", "203.0.113." + i);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        }

        MockHttpServletRequest blockedRequest = new MockHttpServletRequest("POST", "/api/public/tickets/anonymous");
        blockedRequest.setContextPath("/api");
        blockedRequest.setServletPath("/public/tickets/anonymous");
        blockedRequest.setRemoteAddr(clientIp);
        blockedRequest.addHeader("X-Forwarded-For", "203.0.113.99");
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();

        filter.doFilter(blockedRequest, blockedResponse, filterChain);

        assertThat(blockedResponse.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }
}
