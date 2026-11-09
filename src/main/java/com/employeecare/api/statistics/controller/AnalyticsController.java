package com.employeecare.api.statistics.controller;

import com.employeecare.api.generated.apis.AnalyticsApi;

import com.employeecare.api.generated.models.DashboardAnalytics;

import com.employeecare.api.statistics.service.DashboardAnalyticsService;

import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import org.springframework.security.core.Authentication;

import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class AnalyticsController implements AnalyticsApi {

    private final DashboardAnalyticsService analyticsService;

    public AnalyticsController(
            DashboardAnalyticsService analyticsService
    ) {

        this.analyticsService = analyticsService;
    }


    // ==========================================
    // ANALYSES ADMINISTRATEUR
    // ==========================================

    @Override
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public ResponseEntity<DashboardAnalytics>
    getAdminDashboardAnalytics(
            @RequestParam(
                    value = "months",
                    required = false
            )
            Integer months
    ) {

        UUID adminId = currentUserId();

        DashboardAnalytics response =
                analyticsService.getAdminAnalytics(
                        adminId,
                        months
                );

        return ResponseEntity.ok(response);
    }


    // ==========================================
    // ANALYSES RESPONSABLE
    // ==========================================

    @Override
    @PreAuthorize("hasRole('RESPONSABLE')")
    public ResponseEntity<DashboardAnalytics>
    getResponsableDashboardAnalytics(
            @RequestParam(
                    value = "months",
                    required = false
            )
            Integer months
    ) {

        UUID responsableId = currentUserId();

        DashboardAnalytics response =
                analyticsService.getResponsableAnalytics(
                        responsableId,
                        months
                );

        return ResponseEntity.ok(response);
    }


    // ==========================================
    // IDENTIFIER L'UTILISATEUR CONNECTE
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
