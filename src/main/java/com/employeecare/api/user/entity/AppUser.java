package com.employeecare.api.user.entity;

import com.employeecare.api.department.entity.Department;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "app_users")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(
            name = "employee_number",
            length = 40,
            unique = true
    )
    private String employeeNumber;

    @Column(
            name = "first_name",
            nullable = false,
            length = 100
    )
    private String firstName;

    @Column(
            name = "last_name",
            nullable = false,
            length = 100
    )
    private String lastName;

    @Column(
            nullable = false,
            length = 255
    )
    private String email;

    @Column(
            name = "password_hash",
            length = 255
    )
    private String passwordHash;

    @Column(length = 30)
    private String phone;

    @Column(
            name = "job_title",
            length = 150
    )
    private String jobTitle;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 30
    )
    private AccountStatus status =
            AccountStatus.EN_ATTENTE_ACTIVATION;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(
                    name = "user_id"
            ),
            inverseJoinColumns = @JoinColumn(
                    name = "role_code"
            )
    )
    private Set<Role> roles = new HashSet<>();

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