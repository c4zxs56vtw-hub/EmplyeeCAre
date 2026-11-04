package com.employeecare.api.ticket.controller;

import com.employeecare.api.generated.apis.EspaceEmployApi;

import com.employeecare.api.generated.models.CreateTicketRequest;
import com.employeecare.api.generated.models.TicketSummary;

import com.employeecare.api.ticket.service.TicketService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class EmployeeTicketController implements EspaceEmployApi {

    private final TicketService ticketService;

    public EmployeeTicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Override
    @PreAuthorize("hasRole('EMPLOYE')")
    public ResponseEntity<TicketSummary> createMyEmployeeTicket(
            @Valid @RequestBody CreateTicketRequest request
    ) {

        // 1. Récupérer l'authentification courante

        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {

            throw new AuthenticationCredentialsNotFoundException(
                    "Authentification JWT obligatoire."
            );
        }

        // 2. Identifier l'employé connecté

        UUID employeeId = UUID.fromString(
                jwtAuth.getToken().getSubject()
        );

        // 3. Créer le signalement

        TicketSummary response = ticketService.createTicket(
                employeeId,
                request
        );

        // 4. Retourner HTTP 201

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
