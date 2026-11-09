package com.employeecare.api.ticket.repository;

import com.employeecare.api.ticket.entity.TicketAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TicketAttachmentRepository
        extends JpaRepository<TicketAttachment, UUID> {

    long countByTicket_Id(UUID ticketId);
}
