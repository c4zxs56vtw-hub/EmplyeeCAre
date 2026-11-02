package com.employeecare.api.auth.service;

import com.employeecare.api.generated.models.AuthSession;
import com.employeecare.api.generated.models.SessionVerifyResponse;
import com.employeecare.api.generated.models.UserRole;

import com.employeecare.api.user.entity.AppUser;
import com.employeecare.api.user.repository.AppUserRepository;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CurrentSessionService {

    private final AppUserRepository appUserRepository;

    public CurrentSessionService(
            AppUserRepository appUserRepository
    ) {
        this.appUserRepository = appUserRepository;
    }

    @Transactional(readOnly = true)
    public SessionVerifyResponse verifySession(Jwt jwt) {

        // 1. Récupérer l'identifiant de l'utilisateur

        UUID userId;

        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new BadCredentialsException(
                    "Session invalide.",
                    exception
            );
        }

        // 2. Rechercher l'utilisateur dans PostgreSQL

        AppUser user = appUserRepository
                .findByIdWithRoles(userId)
                .orElseThrow(() -> new BadCredentialsException(
                        "Utilisateur introuvable."
                ));

        // 3. Récupérer les rôles actuels depuis PostgreSQL

        List<UserRole> roles = user.getRoles()
                .stream()
                .map(role -> UserRole.fromValue(role.getCode()))
                .toList();

        // 4. Déterminer le rôle principal

        String roleLabel;

        if (roles.contains(UserRole.ADMINISTRATEUR)) {
            roleLabel = "Administrateur";
        } else if (roles.contains(UserRole.RESPONSABLE)) {
            roleLabel = "Responsable";
        } else {
            roleLabel = "Employé";
        }

        // 5. Construire la session

        AuthSession session = new AuthSession();

        session.setUserId(user.getId().toString());

        session.setFullName(
                user.getFirstName() + " " + user.getLastName()
        );

        session.setEmail(user.getEmail());

        session.setRoles(roles);

        session.setRoleLabel(roleLabel);

        session.setIssuedAt(
                jwt.getIssuedAt().toEpochMilli()
        );

        session.setExpiresAt(
                jwt.getExpiresAt().toEpochMilli()
        );

        // 6. Renseigner le département si disponible

        if (user.getDepartment() != null) {

            session.setDepartmentName(
                    user.getDepartment().getName()
            );
        }

        // 7. Renseigner l'identifiant employé

        if (roles.contains(UserRole.EMPLOYE)) {

            session.setEmployeeId(
                    user.getId().toString()
            );
        }

        // 8. Retourner la réponse

        SessionVerifyResponse response =
                new SessionVerifyResponse();

        response.setValid(true);

        response.setSession(session);

        return response;
    }
}