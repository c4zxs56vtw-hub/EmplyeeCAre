package com.employeecare.api.ticket.entity;

import com.employeecare.api.department.entity.Department;

import com.employeecare.api.user.entity.AppUser;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "tickets")
public class Ticket {

    // =====================================
    // IDENTIFICATION
    // =====================================

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(
            name = "reference",
            nullable = false,
            unique = true,
            length = 40
    )
    private String reference;


    // =====================================
    // INFORMATIONS DU SIGNALEMENT
    // =====================================

    @Column(
            name = "title",
            nullable = false,
            length = 200
    )
    private String title;

    @Column(
            name = "description",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String description;


    // =====================================
    // EMPLOYE CREATEUR
    // =====================================

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "employee_id",
            nullable = false
    )
    private AppUser employee;


    // =====================================
    // DEPARTEMENT CONCERNE
    // =====================================

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "department_id",
            nullable = false
    )
    private Department department;


    // =====================================
    // CATEGORIE DU SIGNALEMENT
    // =====================================

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "category_code",
            nullable = false
    )
    private TicketCategory category;


    // =====================================
    // PRIORITE
    // =====================================

    @Enumerated(EnumType.STRING)
    @Column(
            name = "priority",
            nullable = false,
            length = 20
    )
    private TicketPriority priority = TicketPriority.MOYENNE;


    // =====================================
    // STATUT
    // =====================================

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 30
    )
    private TicketStatus status = TicketStatus.EN_ATTENTE;


    // =====================================
    // CONFIDENTIALITE
    // =====================================

    @Column(
            name = "is_confidential",
            nullable = false
    )
    private boolean confidential = false;


    // =====================================
    // DATES
    // =====================================

    @CreationTimestamp
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @UpdateTimestamp
    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;
}
