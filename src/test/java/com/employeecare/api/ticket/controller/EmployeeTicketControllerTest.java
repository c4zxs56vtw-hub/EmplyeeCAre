package com.employeecare.api.ticket.controller;

import com.employeecare.api.generated.models.CreateTicketRequest;
import com.employeecare.api.generated.models.ReviewTicketRequest;
import com.employeecare.api.generated.models.TicketHistoryEvent;
import com.employeecare.api.generated.models.TicketSummary;
import com.employeecare.api.ticket.service.TicketService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeTicketControllerTest {

    @Mock
    private TicketService ticketService;

    @InjectMocks
    private EmployeeTicketController controller;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("getMyEmployeeTickets - Retourne 200 OK avec la liste des signalements de l'employé")
    void getMyEmployeeTickets_shouldReturn200() {
        UUID employeeId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(employeeId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        TicketSummary expectedSummary = new TicketSummary()
                .id(UUID.randomUUID().toString())
                .reference("SIG-2026-000001")
                .title("Impossible d'accéder au réseau");

        when(ticketService.getMyTickets(employeeId)).thenReturn(List.of(expectedSummary));

        ResponseEntity<List<TicketSummary>> response = controller.getMyEmployeeTickets();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(expectedSummary);
    }

    @Test
    @DisplayName("getMyEmployeeTickets - Sans authentification JWT lève AuthenticationCredentialsNotFoundException")
    void getMyEmployeeTickets_withoutJwt_shouldThrow() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> controller.getMyEmployeeTickets())
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    @DisplayName("createMyEmployeeTicket - Retourne 201 Created avec l'identifiant du JWT")
    void createMyEmployeeTicket_shouldReturn201() {
        UUID employeeId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(employeeId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        CreateTicketRequest request = new CreateTicketRequest()
                .title("Impossible d'accéder au réseau")
                .description("Depuis ce matin, je ne parviens plus à accéder au réseau informatique.")
                .categoryCode("TECHNIQUE")
                .isConfidential(false);

        TicketSummary expected = new TicketSummary()
                .id(UUID.randomUUID().toString())
                .reference("SIG-2026-000001")
                .title("Impossible d'accéder au réseau");

        when(ticketService.createTicket(employeeId, request)).thenReturn(expected);

        ResponseEntity<TicketSummary> response = controller.createMyEmployeeTicket(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("createMyEmployeeTicket - Sans authentification JWT lève AuthenticationCredentialsNotFoundException")
    void createMyEmployeeTicket_withoutJwt_shouldThrow() {
        SecurityContextHolder.clearContext();
        CreateTicketRequest request = new CreateTicketRequest();

        assertThatThrownBy(() -> controller.createMyEmployeeTicket(request))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    @DisplayName("reviewMyTicket - Retourne 200 OK avec le signalement évalué")
    void reviewMyTicket_shouldReturn200() {
        UUID employeeId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(employeeId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        ReviewTicketRequest request = new ReviewTicketRequest()
                .action(ReviewTicketRequest.ActionEnum.ACCEPTER_SOLUTION);

        TicketSummary expected = new TicketSummary()
                .id(ticketId.toString())
                .reference("SIG-2026-000001")
                .status(TicketSummary.StatusEnum.RESOLU);

        when(ticketService.reviewTicket(employeeId, ticketId, request)).thenReturn(expected);

        ResponseEntity<TicketSummary> response = controller.reviewMyTicket(ticketId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("reviewMyTicket - Sans authentification JWT lève AuthenticationCredentialsNotFoundException")
    void reviewMyTicket_withoutJwt_shouldThrow() {
        SecurityContextHolder.clearContext();
        UUID ticketId = UUID.randomUUID();
        ReviewTicketRequest request = new ReviewTicketRequest();

        assertThatThrownBy(() -> controller.reviewMyTicket(ticketId, request))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    @DisplayName("closeMyTicket - Retourne 200 OK avec le signalement au statut CLOTURE")
    void closeMyTicket_shouldReturn200() {
        UUID employeeId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(employeeId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        TicketSummary expected = new TicketSummary()
                .id(ticketId.toString())
                .reference("SIG-2026-000001")
                .status(TicketSummary.StatusEnum.CLOTURE);

        when(ticketService.closeTicket(employeeId, ticketId)).thenReturn(expected);

        ResponseEntity<TicketSummary> response = controller.closeMyTicket(ticketId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("closeMyTicket - Sans authentification JWT lève AuthenticationCredentialsNotFoundException")
    void closeMyTicket_withoutJwt_shouldThrow() {
        SecurityContextHolder.clearContext();
        UUID ticketId = UUID.randomUUID();

        assertThatThrownBy(() -> controller.closeMyTicket(ticketId))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    @DisplayName("getMyTicketHistory - Retourne 200 OK avec l'historique du signalement")
    void getMyTicketHistory_shouldReturn200() {
        UUID employeeId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(employeeId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        TicketHistoryEvent event = new TicketHistoryEvent()
                .id(UUID.randomUUID().toString())
                .eventType("CREATION_SIGNALEMENT")
                .actorLabel("Employé");

        when(ticketService.getMyTicketHistory(employeeId, ticketId)).thenReturn(List.of(event));

        ResponseEntity<List<TicketHistoryEvent>> response = controller.getMyTicketHistory(ticketId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(event);
    }
}
