package com.employeecare.api.auth.security;

import com.employeecare.api.user.entity.AccountStatus;
import com.employeecare.api.user.entity.AppUser;
import com.employeecare.api.user.repository.AppUserRepository;

import org.springframework.core.convert.converter.Converter;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class DatabaseJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    private final AppUserRepository appUserRepository;

    public DatabaseJwtAuthenticationConverter(
            AppUserRepository appUserRepository
    ) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {

        // 1. Récupérer l'identifiant contenu dans le JWT

        UUID userId;

        try {

            userId = UUID.fromString(jwt.getSubject());

        } catch (IllegalArgumentException | NullPointerException exception) {

            throw new InvalidBearerTokenException(
                    "Jeton d'authentification invalide."
            );
        }

        // 2. Rechercher l'utilisateur et ses rôles actuels

        AppUser user = appUserRepository
                .findByIdWithRoles(userId)
                .orElseThrow(() -> new InvalidBearerTokenException(
                        "Compte utilisateur introuvable."
                ));

        // 3. Vérifier que le compte est actif

        if (user.getStatus() != AccountStatus.ACTIF) {

            throw new InvalidBearerTokenException(
                    "Le compte utilisateur n'est plus actif."
            );
        }

        // 4. Récupérer les autorisations depuis PostgreSQL

        List<GrantedAuthority> authorities = user.getRoles()
                .stream()
                .map(role -> (GrantedAuthority)
                        new SimpleGrantedAuthority(
                                "ROLE_" + role.getCode()
                        )
                )
                .toList();

        if (authorities.isEmpty()) {

            throw new InvalidBearerTokenException(
                    "Aucune autorisation disponible."
            );
        }

        // 5. Construire l'authentification Spring Security

        return new JwtAuthenticationToken(
                jwt,
                authorities,
                user.getId().toString()
        );
    }
}
