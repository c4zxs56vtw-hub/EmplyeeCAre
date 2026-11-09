package com.employeecare.api.statistics.controller;

import com.employeecare.api.generated.models.DashboardSummary;
import com.employeecare.api.statistics.service.StatisticsService;
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
class StatisticsControllerTest {

    @Mock
    private StatisticsService statisticsService;

    @InjectMocks
    private StatisticsController controller;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("getAdminDashboardStats - Retourne 200 OK avec le DashboardSummary administrateur")
    void getAdminDashboardStats_shouldReturn200() {
        UUID adminId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(adminId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        DashboardSummary expected = new DashboardSummary()
                .scope("ADMINISTRATEUR")
                .totalTickets(42L);

        when(statisticsService.getAdminDashboardStats(adminId)).thenReturn(expected);

        ResponseEntity<DashboardSummary> response = controller.getAdminDashboardStats();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("getResponsableDashboardStats - Retourne 200 OK avec le DashboardSummary responsable")
    void getResponsableDashboardStats_shouldReturn200() {
        UUID responsableId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(responsableId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        DashboardSummary expected = new DashboardSummary()
                .scope("RESPONSABLE")
                .totalTickets(12L)
                .assignedToMe(4L);

        when(statisticsService.getResponsableDashboardStats(responsableId)).thenReturn(expected);

        ResponseEntity<DashboardSummary> response = controller.getResponsableDashboardStats();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
    }
}
