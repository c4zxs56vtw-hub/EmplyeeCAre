package com.employeecare.api.auth.service;

import com.employeecare.api.auth.entity.AccountActivationToken;
import com.employeecare.api.auth.repository.AccountActivationTokenRepository;
import com.employeecare.api.user.entity.AccountStatus;
import com.employeecare.api.user.entity.AppUser;
import com.employeecare.api.user.repository.AppUserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class ActivationTokenService {

    private static final Duration TOKEN_VALIDITY =
            Duration.ofHours(24);

    private static final SecureRandom SECURE_RANDOM =
            new SecureRandom();

    private final AccountActivationTokenRepository tokenRepository;
    private final AppUserRepository appUserRepository;

    public ActivationTokenService(
            AccountActivationTokenRepository tokenRepository,
            AppUserRepository appUserRepository
    ) {
        this.tokenRepository = tokenRepository;
        this.appUserRepository = appUserRepository;
    }

    /**
     * Génère et enregistre un jeton d'activation après avoir
     * verrouillé l'utilisateur et révoqué ses anciens jetons.
     */
    @Transactional
    public String generateToken(AppUser user) {

        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException(
                    "Le compte ne peut pas recevoir de jeton d'activation."
            );
        }

        UUID userId = user.getId();

        // ==========================================
        // 1. VERROUILLER L'UTILISATEUR
        // ==========================================

        AppUser lockedUser = appUserRepository
                .findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Utilisateur introuvable."
                ));

        if (lockedUser.getStatus() != AccountStatus.EN_ATTENTE_ACTIVATION) {
            throw new IllegalArgumentException(
                    "Le compte ne peut pas recevoir de jeton d'activation."
            );
        }

        // ==========================================
        // 2. REVOQUER LES ANCIENS JETONS
        // ==========================================

        tokenRepository.revokePreviousUnusedTokens(
                lockedUser.getId()
        );

        // ==========================================
        // 3. GENERER UN NOUVEAU JETON
        // ==========================================

        byte[] randomBytes = new byte[32];

        SECURE_RANDOM.nextBytes(randomBytes);

        String rawToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        String tokenHash = hashToken(rawToken);

        Instant expirationDate =
                Instant.now().plus(TOKEN_VALIDITY);

        // ==========================================
        // 4. ENREGISTRER LE NOUVEAU JETON
        // ==========================================

        AccountActivationToken activationToken =
                new AccountActivationToken(
                        lockedUser,
                        tokenHash,
                        expirationDate
                );

        tokenRepository.save(activationToken);

        // ==========================================
        // 5. RETOURNER LE RESULTAT
        // ==========================================

        return rawToken;
    }

    /**
     * Recherche un jeton et vérifie sa validité.
     */
    @Transactional
    public AccountActivationToken findValidToken(String rawToken) {

        if (rawToken == null || rawToken.isBlank()) {

            throw new IllegalArgumentException(
                    "Jeton d'activation invalide ou expiré."
            );
        }

        String tokenHash = hashToken(rawToken);

        AccountActivationToken token = tokenRepository
                .findByTokenHashAndUsedAtIsNullAndRevokedAtIsNull(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Jeton d'activation invalide ou expiré."
                ));

        Instant now = Instant.now();

        if (token.getRevokedAt() != null
                || token.getUsedAt() != null
                || token.getExpiresAt() == null
                || !token.getExpiresAt().isAfter(now)
                || !token.isValid(now)
                || token.getUser() == null
                || token.getUser().getStatus()
                != AccountStatus.EN_ATTENTE_ACTIVATION) {

            throw new IllegalArgumentException(
                    "Jeton d'activation invalide ou expiré."
            );
        }

        return token;
    }

    /**
     * Calcule l'empreinte SHA-256 d'un jeton.
     */
    private String hashToken(String rawToken) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    rawToken.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {

            throw new IllegalStateException(
                    "Impossible de calculer l'empreinte du jeton.",
                    exception
            );
        }
    }
}
