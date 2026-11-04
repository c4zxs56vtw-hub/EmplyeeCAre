package com.employeecare.api.ticket.entity;

import com.employeecare.api.user.entity.AppUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TicketEventTest {

    @Test
    @DisplayName("TicketEvent - getters et setters")
    void gettersAndSetters_shouldWorkCorrectly() {
        TicketEvent event = new TicketEvent();
        UUID id = UUID.randomUUID();
        Ticket ticket = new Ticket();
        AppUser actor = new AppUser();
        Instant now = Instant.now();

        event.setId(id);
        event.setTicket(ticket);
        event.setActor(actor);
        event.setEventType("SOUMISSION_VALIDATION");
        event.setOldStatus(TicketStatus.DIAGNOSTIC_EN_COURS);
        event.setNewStatus(TicketStatus.ATTENTE_VALIDATION);
        event.setNote("Intervention terminée et validée.");
        event.setCreatedAt(now);

        assertThat(event.getId()).isEqualTo(id);
        assertThat(event.getTicket()).isSameAs(ticket);
        assertThat(event.getActor()).isSameAs(actor);
        assertThat(event.getEventType()).isEqualTo("SOUMISSION_VALIDATION");
        assertThat(event.getOldStatus()).isEqualTo(TicketStatus.DIAGNOSTIC_EN_COURS);
        assertThat(event.getNewStatus()).isEqualTo(TicketStatus.ATTENTE_VALIDATION);
        assertThat(event.getNote()).isEqualTo("Intervention terminée et validée.");
        assertThat(event.getCreatedAt()).isEqualTo(now);
    }
}
