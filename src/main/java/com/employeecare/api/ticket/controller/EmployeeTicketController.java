package com.employeecare.api.ticket.controller;

import com.employeecare.api.generated.apis.EspaceEmployApi;

import com.employeecare.api.generated.models.CreateTicketRequest;
import com.employeecare.api.generated.models.ReviewTicketRequest;
import com.employeecare.api.generated.models.TicketAttachmentResponse;
import com.employeecare.api.generated.models.TicketHistoryEvent;
import com.employeecare.api.generated.models.TicketSummary;

import com.employeecare.api.ticket.service.TicketAttachmentService;
import com.employeecare.api.ticket.service.TicketService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
public class EmployeeTicketController implements EspaceEmployApi {

    private final TicketService ticketService;
    private final TicketAttachmentService attachmentService;

    public EmployeeTicketController(
            TicketService ticketService,
            TicketAttachmentService attachmentService
    ) {
        this.ticketService = ticketService;
        this.attachmentService = attachmentService;
    }

    @Override
    @PreAuthorize("hasRole('EMPLOYE')")
    public ResponseEntity<List<TicketSummary>> getMyEmployeeTickets() {

        // 1. Récupérer l'authentification courante

        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        // 2. Vérifier qu'il s'agit d'une authentification JWT

        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {

            throw new AuthenticationCredentialsNotFoundException(
                    "Authentification JWT obligatoire."
            );
        }

        // 3. Identifier l'employé connecté

        UUID employeeId = UUID.fromString(
                jwtAuth.getToken().getSubject()
        );

        // 4. Récupérer ses signalements

        List<TicketSummary> tickets = ticketService
                .getMyTickets(employeeId);

        // 5. Retourner la liste

        return ResponseEntity.ok(tickets);
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

    @Override
    @PreAuthorize("hasRole('EMPLOYE')")
    public ResponseEntity<TicketSummary> reviewMyTicket(

            @PathVariable("ticketId")
            UUID ticketId,

            @Valid
            @RequestBody
            ReviewTicketRequest request

    ) {

        // =====================================
        // 1. RECUPERER L'AUTHENTIFICATION
        // =====================================

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {

            throw new AuthenticationCredentialsNotFoundException(
                    "Authentification JWT obligatoire."
            );
        }


        // =====================================
        // 2. IDENTIFIER L'EMPLOYE
        // =====================================

        UUID employeeId = UUID.fromString(
                jwtAuth.getToken().getSubject()
        );


        // =====================================
        // 3. ENREGISTRER SA DECISION
        // =====================================

        TicketSummary response = ticketService.reviewTicket(
                employeeId,
                ticketId,
                request
        );


        // =====================================
        // 4. RETOURNER LE RESULTAT
        // =====================================

        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("hasRole('EMPLOYE')")
    public ResponseEntity<TicketSummary> closeMyTicket(
            @PathVariable("ticketId") UUID ticketId
    ) {

        // =====================================
        // 1. RECUPERER L'AUTHENTIFICATION
        // =====================================

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();


        // =====================================
        // 2. VERIFIER LE JWT
        // =====================================

        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {

            throw new AuthenticationCredentialsNotFoundException(
                    "Authentification JWT obligatoire."
            );
        }


        // =====================================
        // 3. IDENTIFIER L'EMPLOYE CONNECTE
        // =====================================

        UUID employeeId = UUID.fromString(
                jwtAuth.getToken().getSubject()
        );


        // =====================================
        // 4. CLOTURER LE SIGNALEMENT
        // =====================================

        TicketSummary response = ticketService.closeTicket(
                employeeId,
                ticketId
        );


        // =====================================
        // 5. RETOURNER LE RESULTAT
        // =====================================

        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("hasRole('EMPLOYE')")
    public ResponseEntity<List<TicketHistoryEvent>> getMyTicketHistory(
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

        UUID employeeId = UUID.fromString(
                jwtAuth.getToken().getSubject()
        );

        List<TicketHistoryEvent> history =
                ticketService.getMyTicketHistory(
                        employeeId,
                        ticketId
                );

        return ResponseEntity.ok(history);
    }

    @Override
    @PreAuthorize("hasRole('EMPLOYE')")
    public ResponseEntity<TicketAttachmentResponse> uploadMyTicketAttachment(
            @PathVariable("ticketId") UUID ticketId,
            @RequestPart("file") MultipartFile file
    ) {

        // 1. Récupérer l'authentification.

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {

            throw new AuthenticationCredentialsNotFoundException(
                    "Authentification JWT obligatoire."
            );
        }

        // 2. Identifier l'employé connecté.

        UUID employeeId = UUID.fromString(
                jwtAuth.getToken().getSubject()
        );

        // 3. Enregistrer la pièce jointe.

        TicketAttachmentResponse response =
                attachmentService.upload(
                        employeeId,
                        ticketId,
                        file
                );

        // 4. Retourner HTTP 201.

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
