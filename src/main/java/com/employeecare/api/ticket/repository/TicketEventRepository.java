package com.employeecare.api.ticket.repository;

import com.employeecare.api.ticket.entity.TicketEvent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface TicketEventRepository
        extends JpaRepository<TicketEvent, UUID> {

    @Query("""
        SELECT e
        FROM TicketEvent e
        WHERE e.ticket.id = :ticketId
        ORDER BY e.createdAt ASC, e.id ASC
        """)
    List<TicketEvent> findHistoryByTicketId(
            @Param("ticketId") UUID ticketId
    );

    Optional<TicketEvent> findFirstByTicket_IdAndEventTypeOrderByCreatedAtDescIdDesc(
            UUID ticketId,
            String eventType
    );

    @Query("""
        SELECT t.createdAt, e.createdAt
        FROM TicketEvent e
        JOIN e.ticket t
        WHERE e.eventType = :eventType
          AND t.anonymous = false
          AND t.confidential = false
        """)
    List<Object[]> findDashboardDurationsAdmin(

            @Param("eventType")
            String eventType

    );

    @Query("""
        SELECT t.createdAt, e.createdAt
        FROM TicketEvent e
        JOIN e.ticket t
        WHERE e.eventType = :eventType
          AND t.department.id = :departmentId
          AND t.category.code IN :categoryCodes
          AND t.anonymous = false
          AND t.confidential = false
        """)
    List<Object[]> findDashboardDurationsResponsable(

            @Param("eventType")
            String eventType,

            @Param("departmentId")
            UUID departmentId,

            @Param("categoryCodes")
            Set<String> categoryCodes

    );
}
