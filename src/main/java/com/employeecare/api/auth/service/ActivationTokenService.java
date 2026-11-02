package com.employeecare.api.auth.service;

import com.employeecare.api.auth.entity.AccountActivationToken;
import com.employeecare.api.auth.repository.AccountActivationTokenRepository;
import com.employeecare.api.user.entity.AccountStatus;
import com.employeecare.api.user.entity.AppUser;

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

@Service
public class ActivationTokenService {

    private static final Duration TOKEN_VALIDITY =
            Duration.ofHours(24);

    private static final SecureRandom SECURE_RANDOM =
            new SecureRandom();

    private final AccountActivationTokenRepository tokenRepository;

    public ActivationTokenService(
            AccountActivationTokenRepository tokenRepository
    ) {
        this.tokenRepository = tokenRepository;
    }

    /**
     * Génère et enregistre un jeton d'activation.
     */
    @Transactional
    public String generateToken(AppUser user) {

        if (user == null
                || user.getId() == null
                || user.getStatus() != AccountStatus.EN_ATTENTE_ACTIVATION) {

            throw new IllegalArgumentException(
                    "Le compte ne peut pas recevoir de jeton d'activation."
            );
        }

        byte[] randomBytes = new byte[32];

        SECURE_RANDOM.nextBytes(randomBytes);

        String rawToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        String tokenHash = hashToken(rawToken);

        Instant expirationDate =
                Instant.now().plus(TOKEN_VALIDITY);

        AccountActivationToken activationToken =
                new AccountActivationToken(
                        user,
                        tokenHash,
                        expirationDate
                );

        tokenRepository.save(activationToken);

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

        if (!token.isValid(Instant.now())
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