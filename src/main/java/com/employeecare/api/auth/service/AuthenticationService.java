package com.employeecare.api.auth.service;

import com.employeecare.api.generated.models.AuthSession;
import com.employeecare.api.generated.models.LoginRequest;
import com.employeecare.api.generated.models.LoginSuccessResponse;
import com.employeecare.api.generated.models.UserRole;

import com.employeecare.api.user.entity.AccountStatus;
import com.employeecare.api.user.entity.AppUser;
import com.employeecare.api.user.entity.Role;

import com.employeecare.api.user.repository.AppUserRepository;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;

import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class AuthenticationService {

    private final AppUserRepository appUserRepository;

    private final PasswordEncoder passwordEncoder;

    private final JwtService jwtService;

    private final JwtDecoder jwtDecoder;

    public AuthenticationService(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtDecoder jwtDecoder
    ) {

        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtDecoder = jwtDecoder;
    }

    @Transactional(readOnly = true)
    public LoginSuccessResponse login(LoginRequest request) {

        // 1. Vérifier les informations obligatoires

        if (request == null
                || !StringUtils.hasText(request.getEmail())
                || !StringUtils.hasText(request.getPassword())) {

            throw new IllegalArgumentException(
                    "Veuillez renseigner votre email et votre mot de passe."
            );
        }

        // 2. Normaliser l'adresse email

        String email = request.getEmail()
                .trim()
                .toLowerCase(Locale.ROOT);

        // 3. Rechercher l'utilisateur

        AppUser user = appUserRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException(
                        "Adresse email ou mot de passe incorrect."
                ));

        // 4. Vérifier le mot de passe

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(
                request.getPassword(),
                user.getPasswordHash())) {

            throw new BadCredentialsException(
                    "Adresse email ou mot de passe incorrect."
            );
        }

        // 5. Vérifier le statut du compte

        if (user.getStatus() == AccountStatus.DESACTIVE) {

            throw new DisabledException(
                    "Ce compte a été désactivé."
            );
        }

        if (user.getStatus() != AccountStatus.ACTIF) {

            throw new BadCredentialsException(
                    "Identifiants incorrects ou compte non activé."
            );
        }

        // 6. Récupérer les rôles

        List<UserRole> roles = user.getRoles()
                .stream()
                .map(Role::getCode)
                .map(UserRole::valueOf)
                .sorted(Comparator.comparing(UserRole::name))
                .toList();

        if (roles.isEmpty()) {

            throw new IllegalStateException(
                    "Cet utilisateur ne possède aucun rôle."
            );
        }

        // 7. Déterminer l'espace de redirection

        String redirectPath;
        String roleLabel;

        if (roles.contains(UserRole.ADMINISTRATEUR)) {

            redirectPath = "/admin";
            roleLabel = "Administrateur";

        } else if (roles.contains(UserRole.RESPONSABLE)) {

            redirectPath = "/responsable";
            roleLabel = "Responsable";

        } else {

            redirectPath = "/employe";
            roleLabel = "Employé";
        }

        // 8. Générer le JWT

        String token = jwtService.generateAccessToken(user);

        // 9. Récupérer les dates exactes du JWT

        Jwt decodedToken = jwtDecoder.decode(token);

        // 10. Construire la session utilisateur

        AuthSession session = new AuthSession();

        session.setUserId(user.getId().toString());

        if (roles.contains(UserRole.EMPLOYE)) {
            session.setEmployeeId(user.getId().toString());
        }

        session.setFullName(
                user.getFirstName() + " " + user.getLastName()
        );

        session.setEmail(user.getEmail());

        session.setRoles(roles);

        session.setRoleLabel(roleLabel);

        if (user.getDepartment() != null) {

            session.setDepartmentName(
                    user.getDepartment().getName()
            );
        }

        session.setIssuedAt(
                decodedToken.getIssuedAt().toEpochMilli()
        );

        session.setExpiresAt(
                decodedToken.getExpiresAt().toEpochMilli()
        );

        // 11. Construire la réponse OpenAPI

        LoginSuccessResponse response =
                new LoginSuccessResponse();

        response.setSuccess(true);

        response.setToken(token);

        response.setRedirectPath(
                LoginSuccessResponse.RedirectPathEnum
                        .fromValue(redirectPath)
        );

        response.setSession(session);

        return response;
    }
}