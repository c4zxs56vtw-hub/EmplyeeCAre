package com.employeecare.api.maintenance.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenCleanupJob {

    private static final Logger log =
            LoggerFactory.getLogger(TokenCleanupJob.class);

    private final JdbcTemplate jdbcTemplate;
    private final int activationRetentionDays;

    public TokenCleanupJob(
            JdbcTemplate jdbcTemplate,

            @Value(
                    "${employeecare.tokens.activation-retention-days:30}"
            )
            int activationRetentionDays
    ) {
        if (activationRetentionDays < 1) {
            throw new IllegalArgumentException(
                    "La durée de conservation doit être positive."
            );
        }

        this.jdbcTemplate = jdbcTemplate;
        this.activationRetentionDays = activationRetentionDays;
    }

    // ==========================================
    // NETTOYAGE AUTOMATIQUE
    // ==========================================

    @Scheduled(
            cron = "${employeecare.tokens.cleanup-cron:0 0 * * * *}",
            zone = "UTC"
    )
    @Transactional
    public void cleanupExpiredTokens() {

        // ======================================
        // 1. SUPPRIMER LES ANCIENS JWT REVOQUES
        // ======================================

        int deletedJwtTokens = jdbcTemplate.update(
                """
                DELETE FROM revoked_access_tokens
                WHERE expires_at < CURRENT_TIMESTAMP - INTERVAL '2 minutes'
                """
        );

        // ======================================
        // 2. SUPPRIMER LES ANCIENS JETONS
        // D'ACTIVATION
        // ======================================

        int deletedActivationTokens = jdbcTemplate.update(
                """
                DELETE FROM account_activation_tokens
                WHERE expires_at < CURRENT_TIMESTAMP - (
                    CAST(? AS INTEGER) * INTERVAL '1 day'
                )
                """,
                activationRetentionDays
        );

        // ======================================
        // 3. JOURNALISER LE RESULTAT
        // ======================================

        log.info(
                "Nettoyage terminé : JWT révoqués supprimés={}, "
                        + "jetons d'activation supprimés={}",
                deletedJwtTokens,
                deletedActivationTokens
        );
    }
}
