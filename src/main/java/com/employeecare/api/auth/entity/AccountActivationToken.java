package com.employeecare.api.auth.entity;

import com.employeecare.api.user.entity.AppUser;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_activation_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountActivationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(
            name = "token_hash",
            nullable = false,
            unique = true,
            length = 64
    )
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @CreationTimestamp
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    public AccountActivationToken(
            AppUser user,
            String tokenHash,
            Instant expiresAt
    ) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public boolean isValid(Instant now) {
        return usedAt == null
                && revokedAt == null
                && expiresAt.isAfter(now);
    }

    public void markAsUsed(Instant now) {
        if (!isValid(now)) {
            throw new IllegalStateException(
                    "Le jeton d'activation n'est plus valide."
            );
        }

        this.usedAt = now;
    }

    public void revoke(Instant now) {
        if (usedAt == null && revokedAt == null) {
            this.revokedAt = now;
        }
    }
}