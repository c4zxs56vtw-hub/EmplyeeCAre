package com.employeecare.api.maintenance.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenCleanupJobTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("cleanupExpiredTokens - Supprime les JWT révoqués expirés et les anciens jetons d'activation")
    void cleanupExpiredTokens_shouldDeleteExpiredJwtAndActivationTokens() {
        TokenCleanupJob job = new TokenCleanupJob(jdbcTemplate, 30);

        when(jdbcTemplate.update(contains("DELETE FROM revoked_access_tokens")))
                .thenReturn(4);
        when(jdbcTemplate.update(contains("DELETE FROM account_activation_tokens"), eq(30)))
                .thenReturn(2);

        job.cleanupExpiredTokens();

        verify(jdbcTemplate).update(contains("DELETE FROM revoked_access_tokens"));
        verify(jdbcTemplate).update(contains("DELETE FROM account_activation_tokens"), eq(30));
    }

    @Test
    @DisplayName("Constructeur - Refuse une durée de conservation inférieure à 1 jour")
    void constructor_withInvalidRetentionDays_shouldThrow() {
        assertThatThrownBy(() -> new TokenCleanupJob(jdbcTemplate, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("La durée de conservation doit être positive.");
    }
}
