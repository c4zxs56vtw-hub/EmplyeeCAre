package com.employeecare.api.responsable.entity;

import com.employeecare.api.department.entity.Department;
import com.employeecare.api.user.entity.AppUser;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "responsable_accounts")
public class ResponsableAccount {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(
            name = "is_confidential_hab",
            nullable = false
    )
    private boolean confidentialHabilitation = false;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "responsable_assigned_categories",
            joinColumns = @JoinColumn(
                    name = "responsable_user_id"
            )
    )
    @Column(
            name = "category_code",
            nullable = false,
            length = 100
    )
    private Set<String> assignedCategories = new HashSet<>();

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
