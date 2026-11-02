package com.employeecare.api.auth.service;

import org.springframework.jdbc.core.JdbcTemplate;

import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Service
public class JwtRevocationService {

    private final JdbcTemplate jdbcTemplate;

    public JwtRevocationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void revokeToken(Jwt jwt) {

        // 1. Récupérer l'identifiant du JWT

        UUID tokenId = parseUuid(jwt.getId());

        // 2. Récupérer l'identifiant de l'utilisateur

        UUID userId = parseUuid(jwt.getSubject());

        // 3. Vérifier la date d'expiration

        Instant expiresAt = jwt.getExpiresAt();

        if (expiresAt == null ||
                !expiresAt.isAfter(Instant.now())) {

            throw new InvalidBearerTokenException(
                    "Le jeton est expiré ou invalide."
            );
        }

        // 4. Enregistrer la révocation

        String sql = """
                INSERT INTO revoked_access_tokens
                    (jti, user_id, expires_at)
                VALUES (?, ?, ?)
                ON CONFLICT (jti) DO NOTHING
                """;

        jdbcTemplate.update(
                sql,
                tokenId,
                userId,
                Timestamp.from(expiresAt)
        );
    }

    public boolean isRevoked(Jwt jwt) {

        UUID tokenId = parseUuid(jwt.getId());

        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM revoked_access_tokens
                    WHERE jti = ?
                )
                """;

        Boolean revoked = jdbcTemplate.queryForObject(
                sql,
                Boolean.class,
                tokenId
        );

        return Boolean.TRUE.equals(revoked);
    }

    private UUID parseUuid(String value) {

        try {
            return UUID.fromString(value);

        } catch (RuntimeException exception) {

            throw new InvalidBearerTokenException(
                    "Le jeton contient un identifiant invalide."
            );
        }
    }
}