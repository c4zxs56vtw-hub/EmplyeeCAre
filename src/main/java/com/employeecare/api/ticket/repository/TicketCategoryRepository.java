package com.employeecare.api.ticket.repository;

import com.employeecare.api.ticket.entity.TicketCategory;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketCategoryRepository
        extends JpaRepository<TicketCategory, String> {

    boolean existsByCodeAndActiveTrue(String code);
}
