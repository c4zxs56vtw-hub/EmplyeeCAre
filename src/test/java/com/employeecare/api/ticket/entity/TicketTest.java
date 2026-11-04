package com.employeecare.api.ticket.entity;

import com.employeecare.api.department.entity.Department;
import com.employeecare.api.user.entity.AppUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TicketTest {

    @Test
    @DisplayName("Ticket - valeurs par défaut à l'instanciation")
    void defaultValues_shouldBeInitializedCorrectly() {
        Ticket ticket = new Ticket();

        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.MOYENNE);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.EN_ATTENTE);
        assertThat(ticket.isConfidential()).isFalse();
    }

    @Test
    @DisplayName("Ticket - getters et setters")
    void gettersAndSetters_shouldWorkCorrectly() {
        Ticket ticket = new Ticket();
        UUID id = UUID.randomUUID();
        AppUser employee = new AppUser();
        Department department = new Department();
        TicketCategory category = new TicketCategory();
        Instant now = Instant.now();

        ticket.setId(id);
        ticket.setReference("SIG-2026-000001");
        ticket.setTitle("Panne d'imprimante");
        ticket.setDescription("L'imprimante du 2e étage ne répond plus.");
        ticket.setEmployee(employee);
        ticket.setDepartment(department);
        ticket.setCategory(category);
        ticket.setPriority(TicketPriority.HAUTE);
        ticket.setStatus(TicketStatus.DIAGNOSTIC_EN_COURS);
        ticket.setConfidential(true);
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);

        assertThat(ticket.getId()).isEqualTo(id);
        assertThat(ticket.getReference()).isEqualTo("SIG-2026-000001");
        assertThat(ticket.getTitle()).isEqualTo("Panne d'imprimante");
        assertThat(ticket.getDescription()).isEqualTo("L'imprimante du 2e étage ne répond plus.");
        assertThat(ticket.getEmployee()).isSameAs(employee);
        assertThat(ticket.getDepartment()).isSameAs(department);
        assertThat(ticket.getCategory()).isSameAs(category);
        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.HAUTE);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.DIAGNOSTIC_EN_COURS);
        assertThat(ticket.isConfidential()).isTrue();
        assertThat(ticket.getCreatedAt()).isEqualTo(now);
        assertThat(ticket.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("TicketPriority et TicketStatus - valeurs des énumérations")
    void enums_shouldContainExpectedValues() {
        assertThat(TicketPriority.values()).containsExactly(
                TicketPriority.FAIBLE,
                TicketPriority.MOYENNE,
                TicketPriority.HAUTE,
                TicketPriority.CRITIQUE
        );

        assertThat(TicketStatus.values()).containsExactly(
                TicketStatus.EN_ATTENTE,
                TicketStatus.DIAGNOSTIC_EN_COURS,
                TicketStatus.ATTENTE_VALIDATION,
                TicketStatus.RESOLU,
                TicketStatus.CLOTURE,
                TicketStatus.ROUVERT
        );
    }
}
