package com.employeecare.api.responsable.controller;

import com.employeecare.api.generated.apis.EspaceResponsableApi;

import com.employeecare.api.generated.models.TicketSummary;

import com.employeecare.api.ticket.service.TicketService;

import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import org.springframework.security.core.Authentication;

import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class ResponsableTicketController
        implements EspaceResponsableApi {

    private final TicketService ticketService;

    public ResponsableTicketController(
            TicketService ticketService
    ) {

        this.ticketService = ticketService;
    }

    @Override
    @PreAuthorize("hasRole('RESPONSABLE')")
    public ResponseEntity<List<TicketSummary>> getResponsableTickets() {

        // 1. Récupérer l'authentification

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        // 2. Vérifier le JWT

        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {

            throw new AuthenticationCredentialsNotFoundException(
                    "Une authentification JWT est obligatoire."
            );
        }

        // 3. Récupérer l'identifiant du responsable

        UUID responsableId = UUID.fromString(
                jwtAuth.getToken().getSubject()
        );

        // 4. Récupérer les signalements autorisés

        List<TicketSummary> tickets =
                ticketService.getResponsableTickets(
                        responsableId
                );

        // 5. Retourner la réponse

        return ResponseEntity.ok(tickets);
    }
}
