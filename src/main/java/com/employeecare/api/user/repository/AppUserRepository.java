package com.employeecare.api.user.repository;

import com.employeecare.api.user.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<AppUser> findByEmployeeNumber(String employeeNumber);

    boolean existsByRoles_Code(String code);

    @Query("""
        SELECT DISTINCT u
        FROM AppUser u
        LEFT JOIN FETCH u.roles
        WHERE u.id = :id
        """)
    Optional<AppUser> findByIdWithRoles(@Param("id") UUID id);
}