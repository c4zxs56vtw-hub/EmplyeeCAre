package com.employeecare.api.auth.repository;

import com.employeecare.api.auth.entity.AccountActivationToken;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AccountActivationTokenRepository
        extends JpaRepository<AccountActivationToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AccountActivationToken>
    findByTokenHashAndUsedAtIsNullAndRevokedAtIsNull(
            String tokenHash
    );

    @Modifying(flushAutomatically = true)
    @Query(
            value = """
                UPDATE account_activation_tokens
                SET revoked_at = CURRENT_TIMESTAMP
                WHERE user_id = :userId
                  AND revoked_at IS NULL
                  AND used_at IS NULL
                """,
            nativeQuery = true
    )
    int revokePreviousUnusedTokens(
            @Param("userId") UUID userId
    );
}
