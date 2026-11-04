package com.employeecare.api.ticket.repository;

import com.employeecare.api.ticket.entity.Ticket;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketRepository
        extends JpaRepository<Ticket, UUID> {

    @EntityGraph(attributePaths = "category")
    List<Ticket> findByEmployee_IdOrderByCreatedAtDesc(
            UUID employeeId
    );

}
