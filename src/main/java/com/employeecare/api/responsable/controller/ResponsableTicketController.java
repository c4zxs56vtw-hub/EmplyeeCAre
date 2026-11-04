package com.employeecare.api.responsable.controller;

import com.employeecare.api.generated.apis.EspaceResponsableApi;

import com.employeecare.api.generated.models.TicketHistoryEvent;
import com.employeecare.api.generated.models.TicketSummary;
import com.employeecare.api.generated.models.UpdateTicketStatusRequest;

import com.employeecare.api.ticket.service.TicketService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import org.springframework.security.core.Authentication;

import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
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

    @Override
    @PreAuthorize("hasRole('RESPONSABLE')")
    public ResponseEntity<TicketSummary> takeChargeTicket(
            @PathVariable("ticketId") String ticketId
    ) {

        // 1. Récupérer l'authentification

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {

            throw new AuthenticationCredentialsNotFoundException(
                    "Authentification JWT obligatoire."
            );
        }

        // 2. Identifier le responsable connecté

        UUID responsableId = UUID.fromString(
                jwtAuth.getToken().getSubject()
        );

        // 3. Identifier le signalement

        UUID targetTicketId;

        try {

            targetTicketId = UUID.fromString(ticketId);

        } catch (IllegalArgumentException exception) {

            return ResponseEntity.badRequest().build();
        }

        // 4. Prendre le signalement en charge

        TicketSummary response = ticketService.takeCharge(
                responsableId,
                targetTicketId
        );

        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("hasRole('RESPONSABLE')")
    public ResponseEntity<TicketSummary> updateResponsableTicketStatus(

            @PathVariable("ticketId") UUID ticketId,

            @Valid @RequestBody UpdateTicketStatusRequest request
    ) {

        // 1. Récupérer l'authentification

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {

            throw new AuthenticationCredentialsNotFoundException(
                    "Authentification JWT obligatoire."
            );
        }

        // 2. Identifier le responsable connecté

        UUID responsableId = UUID.fromString(
                jwtAuth.getToken().getSubject()
        );

        // 3. Soumettre le signalement à validation

        TicketSummary response =
                ticketService.submitForValidation(
                        responsableId,
                        ticketId,
                        request
                );

        // 4. Retourner le résultat

        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("hasRole('RESPONSABLE')")
    public ResponseEntity<TicketSummary> resumeTicket(

            @PathVariable("ticketId") UUID ticketId
    ) {

        // 1. Récupérer l'authentification

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {

            throw new AuthenticationCredentialsNotFoundException(
                    "Authentification JWT obligatoire."
            );
        }

        // 2. Identifier le responsable connecté

        UUID responsableId = UUID.fromString(
                jwtAuth.getToken().getSubject()
        );

        // 3. Reprendre le traitement du signalement

        TicketSummary response =
                ticketService.resumeTicket(
                        responsableId,
                        ticketId
                );

        // 4. Retourner la réponse

        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("hasRole('RESPONSABLE')")
    public ResponseEntity<List<TicketHistoryEvent>> getResponsableTicketHistory(
            @PathVariable("ticketId") UUID ticketId
    ) {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {

            throw new AuthenticationCredentialsNotFoundException(
                    "Authentification JWT obligatoire."
            );
        }

        UUID responsableId = UUID.fromString(
                jwtAuth.getToken().getSubject()
        );

        List<TicketHistoryEvent> history =
                ticketService.getResponsableTicketHistory(
                        responsableId,
                        ticketId
                );

        return ResponseEntity.ok(history);
    }
}
