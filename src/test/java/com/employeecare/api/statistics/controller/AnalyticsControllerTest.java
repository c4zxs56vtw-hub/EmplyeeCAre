package com.employeecare.api.statistics.controller;

import com.employeecare.api.generated.models.DashboardAnalytics;
import com.employeecare.api.statistics.service.DashboardAnalyticsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsControllerTest {

    @Mock
    private DashboardAnalyticsService analyticsService;

    @InjectMocks
    private AnalyticsController controller;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("getAdminDashboardAnalytics - Retourne 200 OK avec le DashboardAnalytics administrateur")
    void getAdminDashboardAnalytics_shouldReturn200() {
        UUID adminId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(adminId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        DashboardAnalytics expected = new DashboardAnalytics()
                .scope("ADMINISTRATEUR")
                .months(6);

        when(analyticsService.getAdminAnalytics(adminId, 6)).thenReturn(expected);

        ResponseEntity<DashboardAnalytics> response = controller.getAdminDashboardAnalytics(6);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("getResponsableDashboardAnalytics - Retourne 200 OK avec le DashboardAnalytics responsable")
    void getResponsableDashboardAnalytics_shouldReturn200() {
        UUID responsableId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(responsableId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        DashboardAnalytics expected = new DashboardAnalytics()
                .scope("RESPONSABLE")
                .months(6);

        when(analyticsService.getResponsableAnalytics(responsableId, 6)).thenReturn(expected);

        ResponseEntity<DashboardAnalytics> response = controller.getResponsableDashboardAnalytics(6);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
    }
}
