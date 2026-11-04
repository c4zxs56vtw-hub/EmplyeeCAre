package com.employeecare.api.ticket.entity;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ticket_categories")
public class TicketCategory {

    @Id
    @Column(
            name = "code",
            length = 100
    )
    private String code;

    @Column(
            name = "label",
            nullable = false,
            length = 150
    )
    private String label;

    @Column(
            name = "active",
            nullable = false
    )
    private boolean active = true;
}
