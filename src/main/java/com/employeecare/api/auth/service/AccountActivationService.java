package com.employeecare.api.auth.service;

import com.employeecare.api.auth.entity.AccountActivationToken;

import com.employeecare.api.user.entity.AccountStatus;
import com.employeecare.api.user.entity.AppUser;

import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Service
public class AccountActivationService {

    private final ActivationTokenService activationTokenService;

    private final PasswordEncoder passwordEncoder;

    public AccountActivationService(
            ActivationTokenService activationTokenService,
            PasswordEncoder passwordEncoder
    ) {
        this.activationTokenService = activationTokenService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UUID activateAccount(
            String invitationToken,
            String newPassword
    ) {

        // 1. Vérifier le mot de passe

        validatePassword(newPassword);

        // 2. Rechercher et vérifier le jeton

        AccountActivationToken token =
                activationTokenService.findValidToken(
                        invitationToken
                );

        // 3. Récupérer l'utilisateur associé

        AppUser user = token.getUser();

        // 4. Vérifier le statut du compte

        if (user.getStatus()
                != AccountStatus.EN_ATTENTE_ACTIVATION) {

            throw new IllegalStateException(
                    "Ce compte ne peut pas être activé."
            );
        }

        // 5. Calculer l'empreinte sécurisée du mot de passe

        String passwordHash =
                passwordEncoder.encode(newPassword);

        // 6. Enregistrer l'empreinte du mot de passe

        user.setPasswordHash(passwordHash);

        // 7. Activer le compte

        user.setStatus(AccountStatus.ACTIF);

        // 8. Marquer le jeton comme utilisé

        token.markAsUsed(Instant.now());

        // 9. Retourner l'identifiant de l'utilisateur

        return user.getId();
    }

    private void validatePassword(String password) {

        if (password == null || password.length() < 8) {

            throw new IllegalArgumentException(
                    "Le mot de passe doit contenir au moins 8 caractères."
            );
        }

        int passwordBytes = password
                .getBytes(StandardCharsets.UTF_8)
                .length;

        if (passwordBytes > 72) {

            throw new IllegalArgumentException(
                    "Le mot de passe est trop long."
            );
        }
    }
}