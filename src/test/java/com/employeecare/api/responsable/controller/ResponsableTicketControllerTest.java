package com.employeecare.api.responsable.controller;

import com.employeecare.api.generated.models.TicketAttachmentResponse;
import com.employeecare.api.generated.models.TicketHistoryEvent;
import com.employeecare.api.generated.models.TicketSummary;
import com.employeecare.api.generated.models.UpdateTicketStatusRequest;
import com.employeecare.api.ticket.service.TicketAttachmentService;
import com.employeecare.api.ticket.service.TicketAttachmentService.DownloadData;
import com.employeecare.api.ticket.service.TicketService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResponsableTicketControllerTest {

    @Mock
    private TicketService ticketService;

    @Mock
    private TicketAttachmentService attachmentService;

    @InjectMocks
    private ResponsableTicketController controller;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("getResponsableTickets - Retourne 200 OK avec la liste des signalements autorisés")
    void getResponsableTickets_shouldReturn200() {
        UUID responsableId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(responsableId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        TicketSummary summary = new TicketSummary()
                .id(UUID.randomUUID().toString())
                .reference("SIG-2026-000001")
                .title("Problème administratif");

        when(ticketService.getResponsableTickets(responsableId)).thenReturn(List.of(summary));

        ResponseEntity<List<TicketSummary>> response = controller.getResponsableTickets();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(summary);
    }

    @Test
    @DisplayName("getResponsableTickets - Sans JWT lève AuthenticationCredentialsNotFoundException")
    void getResponsableTickets_withoutJwt_shouldThrow() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> controller.getResponsableTickets())
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    @DisplayName("takeChargeTicket - Retourne 200 OK avec le signalement pris en charge")
    void takeChargeTicket_shouldReturn200() {
        UUID responsableId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(responsableId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        TicketSummary summary = new TicketSummary()
                .id(ticketId.toString())
                .reference("SIG-2026-000001")
                .status(TicketSummary.StatusEnum.DIAGNOSTIC_EN_COURS);

        when(ticketService.takeCharge(responsableId, ticketId)).thenReturn(summary);

        ResponseEntity<TicketSummary> response = controller.takeChargeTicket(ticketId.toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(summary);
    }

    @Test
    @DisplayName("takeChargeTicket - Retourne 400 Bad Request si ticketId n'est pas un UUID valide")
    void takeChargeTicket_invalidUuid_shouldReturn400() {
        UUID responsableId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(responsableId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        ResponseEntity<TicketSummary> response = controller.takeChargeTicket("invalid-uuid");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(ticketService);
    }

    @Test
    @DisplayName("updateResponsableTicketStatus - Retourne 200 OK avec le signalement en ATTENTE_VALIDATION")
    void updateResponsableTicketStatus_shouldReturn200() {
        UUID responsableId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(responsableId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        UpdateTicketStatusRequest request = new UpdateTicketStatusRequest()
                .status(UpdateTicketStatusRequest.StatusEnum.ATTENTE_VALIDATION)
                .note("Le problème de connexion a été corrigé.");

        TicketSummary summary = new TicketSummary()
                .id(ticketId.toString())
                .reference("SIG-2026-000001")
                .status(TicketSummary.StatusEnum.ATTENTE_VALIDATION);

        when(ticketService.submitForValidation(responsableId, ticketId, request)).thenReturn(summary);

        ResponseEntity<TicketSummary> response = controller.updateResponsableTicketStatus(ticketId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(summary);
    }

    @Test
    @DisplayName("updateResponsableTicketStatus - Sans JWT lève AuthenticationCredentialsNotFoundException")
    void updateResponsableTicketStatus_withoutJwt_shouldThrow() {
        SecurityContextHolder.clearContext();
        UUID ticketId = UUID.randomUUID();
        UpdateTicketStatusRequest request = new UpdateTicketStatusRequest();

        assertThatThrownBy(() -> controller.updateResponsableTicketStatus(ticketId, request))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    @DisplayName("resumeTicket - Retourne 200 OK avec le signalement en DIAGNOSTIC_EN_COURS")
    void resumeTicket_shouldReturn200() {
        UUID responsableId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(responsableId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        TicketSummary summary = new TicketSummary()
                .id(ticketId.toString())
                .reference("SIG-2026-000001")
                .status(TicketSummary.StatusEnum.DIAGNOSTIC_EN_COURS);

        when(ticketService.resumeTicket(responsableId, ticketId)).thenReturn(summary);

        ResponseEntity<TicketSummary> response = controller.resumeTicket(ticketId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(summary);
    }

    @Test
    @DisplayName("resumeTicket - Sans JWT lève AuthenticationCredentialsNotFoundException")
    void resumeTicket_withoutJwt_shouldThrow() {
        SecurityContextHolder.clearContext();
        UUID ticketId = UUID.randomUUID();

        assertThatThrownBy(() -> controller.resumeTicket(ticketId))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    @DisplayName("getResponsableTicketHistory - Retourne 200 OK avec l'historique du signalement")
    void getResponsableTicketHistory_shouldReturn200() {
        UUID responsableId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(responsableId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        TicketHistoryEvent event = new TicketHistoryEvent()
                .id(UUID.randomUUID().toString())
                .eventType("PRISE_EN_CHARGE")
                .actorLabel("Responsable");

        when(ticketService.getResponsableTicketHistory(responsableId, ticketId)).thenReturn(List.of(event));

        ResponseEntity<List<TicketHistoryEvent>> response = controller.getResponsableTicketHistory(ticketId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(event);
    }

    @Test
    @DisplayName("listResponsableTicketAttachments - Retourne 200 OK avec la liste des pièces jointes autorisées")
    void listResponsableTicketAttachments_shouldReturn200() {
        UUID responsableId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(responsableId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        TicketAttachmentResponse item = new TicketAttachmentResponse()
                .id(attachmentId)
                .fileName("capture.png")
                .contentType("image/png")
                .sizeBytes(182450L);

        when(attachmentService.listForResponsable(responsableId, ticketId)).thenReturn(List.of(item));

        ResponseEntity<List<TicketAttachmentResponse>> response =
                controller.listResponsableTicketAttachments(ticketId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(item);
    }

    @Test
    @DisplayName("downloadResponsableTicketAttachment - Retourne 200 OK avec les en-têtes sécurisés et la ressource binaire")
    void downloadResponsableTicketAttachment_shouldReturn200WithHeaders() {
        UUID responsableId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(responsableId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        byte[] content = "%PDF-1.7 sample".getBytes(StandardCharsets.US_ASCII);
        Resource resource = new ByteArrayResource(content);
        DownloadData data = new DownloadData(resource, "rapport_incident.pdf", content.length);

        when(attachmentService.downloadForResponsable(responsableId, ticketId, attachmentId)).thenReturn(data);

        ResponseEntity<Resource> response =
                controller.downloadResponsableTicketAttachment(ticketId, attachmentId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(response.getHeaders().getContentLength()).isEqualTo(content.length);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("rapport_incident.pdf");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getBody()).isSameAs(resource);
    }
}
