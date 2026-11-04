package com.employeecare.api.ticket.repository;

import com.employeecare.api.ticket.entity.TicketEvent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TicketEventRepository
        extends JpaRepository<TicketEvent, UUID> {

}
