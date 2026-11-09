package com.employeecare.api.statistics.controller;

import com.employeecare.api.generated.apis.StatisticsApi;

import com.employeecare.api.generated.models.DashboardSummary;

import com.employeecare.api.statistics.service.StatisticsService;

import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import org.springframework.security.core.Authentication;

import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class StatisticsController implements StatisticsApi {

    private final StatisticsService statisticsService;

    public StatisticsController(
            StatisticsService statisticsService
    ) {

        this.statisticsService = statisticsService;
    }


    // ==========================================
    // STATISTIQUES ADMINISTRATEUR
    // ==========================================

    @Override
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public ResponseEntity<DashboardSummary>
    getAdminDashboardStats() {

        UUID adminId = currentUserId();

        DashboardSummary response =
                statisticsService.getAdminDashboardStats(
                        adminId
                );

        return ResponseEntity.ok(response);
    }


    // ==========================================
    // STATISTIQUES RESPONSABLE
    // ==========================================

    @Override
    @PreAuthorize("hasRole('RESPONSABLE')")
    public ResponseEntity<DashboardSummary>
    getResponsableDashboardStats() {

        UUID responsableId = currentUserId();

        DashboardSummary response =
                statisticsService.getResponsableDashboardStats(
                        responsableId
                );

        return ResponseEntity.ok(response);
    }


    // ==========================================
    // UTILITAIRE JWT
    // ==========================================

    private UUID currentUserId() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {

            throw new AuthenticationCredentialsNotFoundException(
                    "Authentification JWT obligatoire."
            );
        }

        return UUID.fromString(
                jwtAuth.getToken().getSubject()
        );
    }
}
