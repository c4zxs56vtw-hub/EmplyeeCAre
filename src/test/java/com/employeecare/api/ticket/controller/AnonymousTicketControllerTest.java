package com.employeecare.api.ticket.controller;

import com.employeecare.api.generated.models.AnonymousTicketActionResponse;
import com.employeecare.api.generated.models.AnonymousTicketCloseRequest;
import com.employeecare.api.generated.models.AnonymousTicketCreatedResponse;
import com.employeecare.api.generated.models.AnonymousTicketLookupRequest;
import com.employeecare.api.generated.models.AnonymousTicketLookupResponse;
import com.employeecare.api.generated.models.AnonymousTicketReviewRequest;
import com.employeecare.api.generated.models.CreateAnonymousTicketRequest;
import com.employeecare.api.ticket.service.AnonymousTicketService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnonymousTicketControllerTest {

    @Mock
    private AnonymousTicketService anonymousTicketService;

    @InjectMocks
    private AnonymousTicketController controller;

    @Test
    @DisplayName("createAnonymousTicket - Retourne 201 Created avec Cache-Control no-store")
    void createAnonymousTicket_shouldReturn201WithNoStore() {
        UUID ticketId = UUID.randomUUID();
        CreateAnonymousTicketRequest request = new CreateAnonymousTicketRequest()
                .title("Situation de harcèlement")
                .description("Description détaillée de la situation professionnelle.")
                .categoryCode("RH")
                .departmentId(UUID.randomUUID());

        AnonymousTicketCreatedResponse expected = new AnonymousTicketCreatedResponse()
                .ticketId(ticketId)
                .reference("SIG-2026-000125")
                .accessCode("gV8nS6AqK7R3xB5tW2pL9mH4cY1dF0uZ8jN6eQ3sT5A");

        when(anonymousTicketService.create(request)).thenReturn(expected);

        ResponseEntity<AnonymousTicketCreatedResponse> response =
                controller.createAnonymousTicket(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("lookupAnonymousTicket - Retourne 200 OK avec Cache-Control no-store et Pragma no-cache")
    void lookupAnonymousTicket_shouldReturn200WithNoCacheHeaders() {
        AnonymousTicketLookupRequest request = new AnonymousTicketLookupRequest()
                .reference("SIG-2026-000125")
                .accessCode("gV8nS6AqK7R3xB5tW2pL9mH4cY1dF0uZ8jN6eQ3sT5A");

        AnonymousTicketLookupResponse expected = new AnonymousTicketLookupResponse()
                .reference("SIG-2026-000125")
                .title("Problème professionnel confidentiel")
                .status("EN_ATTENTE");

        when(anonymousTicketService.lookupAnonymousTicket(request)).thenReturn(expected);

        ResponseEntity<AnonymousTicketLookupResponse> response =
                controller.lookupAnonymousTicket(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        assertThat(response.getHeaders().getFirst("Pragma")).isEqualTo("no-cache");
        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("reviewAnonymousTicket - Retourne 200 OK avec Cache-Control no-store")
    void reviewAnonymousTicket_shouldReturn200WithNoStore() {
        AnonymousTicketReviewRequest request = new AnonymousTicketReviewRequest()
                .reference("SIG-2026-000125")
                .accessCode("gV8nS6AqK7R3xB5tW2pL9mH4cY1dF0uZ8jN6eQ3sT5A")
                .action(AnonymousTicketReviewRequest.ActionEnum.ACCEPTER_SOLUTION);

        AnonymousTicketActionResponse expected = new AnonymousTicketActionResponse()
                .reference("SIG-2026-000125")
                .status("RESOLU");

        when(anonymousTicketService.reviewAnonymousTicket(request)).thenReturn(expected);

        ResponseEntity<AnonymousTicketActionResponse> response =
                controller.reviewAnonymousTicket(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("closeAnonymousTicket - Retourne 200 OK avec Cache-Control no-store")
    void closeAnonymousTicket_shouldReturn200WithNoStore() {
        AnonymousTicketCloseRequest request = new AnonymousTicketCloseRequest()
                .reference("SIG-2026-000125")
                .accessCode("gV8nS6AqK7R3xB5tW2pL9mH4cY1dF0uZ8jN6eQ3sT5A");

        AnonymousTicketActionResponse expected = new AnonymousTicketActionResponse()
                .reference("SIG-2026-000125")
                .status("CLOTURE");

        when(anonymousTicketService.closeAnonymousTicket(request)).thenReturn(expected);

        ResponseEntity<AnonymousTicketActionResponse> response =
                controller.closeAnonymousTicket(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        assertThat(response.getBody()).isSameAs(expected);
    }
}
