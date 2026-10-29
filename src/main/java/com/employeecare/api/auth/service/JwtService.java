package com.employeecare.api.auth.service;

import com.employeecare.api.user.entity.AccountStatus;
import com.employeecare.api.user.entity.AppUser;
import com.employeecare.api.user.entity.Role;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

import java.util.List;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;

    private final String issuer;

    private final Duration tokenValidity;

    public JwtService(
            JwtEncoder jwtEncoder,

            @Value("${employeecare.jwt.issuer}")
            String issuer,

            @Value("${employeecare.jwt.access-token-ttl}")
            Duration tokenValidity
    ) {

        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.tokenValidity = tokenValidity;

        if (tokenValidity.isZero() || tokenValidity.isNegative()) {
            throw new IllegalArgumentException(
                    "La durée de validité du JWT doit être positive."
            );
        }
    }

    public String generateAccessToken(AppUser user) {

        // 1. Vérifier le compte

        if (user == null
                || user.getId() == null
                || user.getStatus() != AccountStatus.ACTIF) {

            throw new IllegalArgumentException(
                    "Impossible de générer un JWT pour ce compte."
            );
        }

        // 2. Récupérer les rôles

        List<String> roles = user.getRoles()
                .stream()
                .map(Role::getCode)
                .sorted()
                .toList();

        if (roles.isEmpty()) {

            throw new IllegalStateException(
                    "L'utilisateur ne possède aucun rôle."
            );
        }

        // 3. Définir les dates

        Instant now = Instant.now();

        Instant expiration = now.plus(tokenValidity);

        // 4. Construire les informations du JWT

        JwtClaimsSet claims = JwtClaimsSet.builder()

                .issuer(issuer)

                .subject(user.getId().toString())

                .issuedAt(now)

                .expiresAt(expiration)

                .id(UUID.randomUUID().toString())

                .claim("roles", roles)

                .build();

        // 5. Définir l'algorithme de signature

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .build();

        // 6. Générer le JWT signé

        return jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }
}