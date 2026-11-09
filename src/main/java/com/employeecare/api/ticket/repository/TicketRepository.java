package com.employeecare.api.ticket.repository;

import com.employeecare.api.ticket.entity.Ticket;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface TicketRepository
        extends JpaRepository<Ticket, UUID> {

    @EntityGraph(attributePaths = "category")
    List<Ticket> findByEmployee_IdOrderByCreatedAtDesc(
            UUID employeeId
    );

    @Query("""
        SELECT t
        FROM Ticket t

        JOIN FETCH t.category

        WHERE t.department.id = :departmentId

        AND t.category.code IN :categoryCodes

        AND (
            t.confidential = false
            OR :canReadConfidential = true
        )

        ORDER BY t.createdAt DESC
        """)
    List<Ticket> findVisibleForResponsable(

            @Param("departmentId")
            UUID departmentId,

            @Param("categoryCodes")
            Set<String> categoryCodes,

            @Param("canReadConfidential")
            boolean canReadConfidential

    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT t
        FROM Ticket t
        WHERE t.id = :ticketId
        """)
    Optional<Ticket> findByIdForUpdate(
            @Param("ticketId") UUID ticketId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT t
        FROM Ticket t
        WHERE t.id = :ticketId
          AND t.employee.id = :employeeId
        """)
    Optional<Ticket> findOwnedByIdForUpdate(

            @Param("ticketId")
            UUID ticketId,

            @Param("employeeId")
            UUID employeeId

    );

    boolean existsByIdAndEmployee_Id(
            UUID id,
            UUID employeeId
    );

    Optional<Ticket> findByReferenceAndAnonymousTrue(
            String reference
    );

    @Query("""
        SELECT t.status, COUNT(t)
        FROM Ticket t
        WHERE t.anonymous = false
          AND t.confidential = false
        GROUP BY t.status
        """)
    List<Object[]> countDashboardByStatusAdmin();

    @Query("""
        SELECT t.category.code, COUNT(t)
        FROM Ticket t
        WHERE t.anonymous = false
          AND t.confidential = false
        GROUP BY t.category.code
        ORDER BY t.category.code
        """)
    List<Object[]> countDashboardByCategoryAdmin();

    @Query("""
        SELECT t.status, COUNT(t)
        FROM Ticket t
        WHERE t.department.id = :departmentId
          AND t.category.code IN :categoryCodes
          AND t.anonymous = false
          AND t.confidential = false
        GROUP BY t.status
        """)
    List<Object[]> countDashboardByStatusResponsable(

            @Param("departmentId")
            UUID departmentId,

            @Param("categoryCodes")
            Set<String> categoryCodes

    );

    @Query("""
        SELECT t.category.code, COUNT(t)
        FROM Ticket t
        WHERE t.department.id = :departmentId
          AND t.category.code IN :categoryCodes
          AND t.anonymous = false
          AND t.confidential = false
        GROUP BY t.category.code
        ORDER BY t.category.code
        """)
    List<Object[]> countDashboardByCategoryResponsable(

            @Param("departmentId")
            UUID departmentId,

            @Param("categoryCodes")
            Set<String> categoryCodes

    );

    @Query("""
        SELECT COUNT(t)
        FROM Ticket t
        WHERE t.assignedResponsable.id = :responsableId
          AND t.department.id = :departmentId
          AND t.category.code IN :categoryCodes
          AND t.anonymous = false
          AND t.confidential = false
        """)
    long countDashboardAssignedToResponsable(

            @Param("responsableId")
            UUID responsableId,

            @Param("departmentId")
            UUID departmentId,

            @Param("categoryCodes")
            Set<String> categoryCodes

    );

}
