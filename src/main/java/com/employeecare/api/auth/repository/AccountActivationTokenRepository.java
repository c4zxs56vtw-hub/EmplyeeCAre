package com.employeecare.api.auth.repository;

import com.employeecare.api.auth.entity.AccountActivationToken;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface AccountActivationTokenRepository
        extends JpaRepository<AccountActivationToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AccountActivationToken>
    findByTokenHashAndUsedAtIsNullAndRevokedAtIsNull(
            String tokenHash
    );
}