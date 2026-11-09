package com.employeecare.api.ticket.controller;

import com.employeecare.api.generated.models.AnonymousTicketCreatedResponse;
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
}
