package com.employeecare.api.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtRevocationServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private JwtRevocationService service;

    @BeforeEach
    void setUp() {
        service = new JwtRevocationService(jdbcTemplate);
    }

    @Test
    void revokeToken_jetonValide_insereDansLaBase() {

        UUID tokenId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant futureExpiry = Instant.now().plus(1, ChronoUnit.HOURS);

        Jwt jwt = Jwt.withTokenValue("valid-token")
                .header("alg", "HS256")
                .jti(tokenId.toString())
                .subject(userId.toString())
                .expiresAt(futureExpiry)
                .build();

        service.revokeToken(jwt);

        verify(jdbcTemplate).update(
                anyString(),
                eq(tokenId),
                eq(userId),
                eq(Timestamp.from(futureExpiry))
        );
    }

    @Test
    void revokeToken_jetonExpire_leveInvalidBearerTokenException() {

        UUID tokenId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant pastExpiry = Instant.now().minus(1, ChronoUnit.HOURS);

        Jwt jwt = Jwt.withTokenValue("expired-token")
                .header("alg", "HS256")
                .jti(tokenId.toString())
                .subject(userId.toString())
                .expiresAt(pastExpiry)
                .build();

        assertThatThrownBy(() -> service.revokeToken(jwt))
                .isInstanceOf(InvalidBearerTokenException.class);

        verify(jdbcTemplate, never()).update(anyString(), any(), any(), any());
    }

    @Test
    void revokeToken_jtiInvalide_leveInvalidBearerTokenException() {

        UUID userId = UUID.randomUUID();
        Instant futureExpiry = Instant.now().plus(1, ChronoUnit.HOURS);

        Jwt jwt = Jwt.withTokenValue("invalid-jti-token")
                .header("alg", "HS256")
                .jti("non-uuid-jti")
                .subject(userId.toString())
                .expiresAt(futureExpiry)
                .build();

        assertThatThrownBy(() -> service.revokeToken(jwt))
                .isInstanceOf(InvalidBearerTokenException.class)
                .hasMessageContaining("identifiant invalide");
    }

    @Test
    void isRevoked_jetonPresentEnBase_retourneTrue() {

        UUID tokenId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .jti(tokenId.toString())
                .subject(UUID.randomUUID().toString())
                .build();

        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq(tokenId)))
                .thenReturn(true);

        boolean revoked = service.isRevoked(jwt);

        assertThat(revoked).isTrue();
    }

    @Test
    void isRevoked_jetonAbsentDeLaBase_retourneFalse() {

        UUID tokenId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .jti(tokenId.toString())
                .subject(UUID.randomUUID().toString())
                .build();

        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), eq(tokenId)))
                .thenReturn(false);

        boolean revoked = service.isRevoked(jwt);

        assertThat(revoked).isFalse();
    }
}
