package com.employeecare.api.ticket.repository;

import com.employeecare.api.ticket.entity.TicketAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketAttachmentRepository
        extends JpaRepository<TicketAttachment, UUID> {

    long countByTicket_Id(UUID ticketId);

    @Query("""
            SELECT a
            FROM TicketAttachment a
            WHERE a.ticket.id = :ticketId
            ORDER BY a.createdAt ASC, a.id ASC
            """)
    List<TicketAttachment> findForTicket(
            @Param("ticketId") UUID ticketId
    );

    Optional<TicketAttachment> findByIdAndTicket_Id(
            UUID attachmentId,
            UUID ticketId
    );
}
