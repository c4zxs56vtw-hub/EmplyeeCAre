package com.employeecare.api.auth.controller;

import com.employeecare.api.auth.service.AccountActivationService;
import com.employeecare.api.auth.service.AuthenticationService;
import com.employeecare.api.auth.service.CurrentSessionService;
import com.employeecare.api.auth.service.JwtRevocationService;

import com.employeecare.api.generated.apis.AuthentificationApi;

import com.employeecare.api.generated.models.ActivateAccountRequest;
import com.employeecare.api.generated.models.LoginRequest;
import com.employeecare.api.generated.models.LoginSuccessResponse;
import com.employeecare.api.generated.models.LogoutUser200Response;
import com.employeecare.api.generated.models.SessionVerifyResponse;

import com.employeecare.api.user.entity.AppUser;
import com.employeecare.api.user.repository.AppUserRepository;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class AuthenticationController implements AuthentificationApi {

    private final AuthenticationService authenticationService;

    private final AccountActivationService accountActivationService;

    private final AppUserRepository appUserRepository;

    private final CurrentSessionService currentSessionService;

    private final JwtRevocationService jwtRevocationService;

    public AuthenticationController(
            AuthenticationService authenticationService,
            AccountActivationService accountActivationService,
            AppUserRepository appUserRepository,
            CurrentSessionService currentSessionService,
            JwtRevocationService jwtRevocationService
    ) {
        this.authenticationService = authenticationService;
        this.accountActivationService = accountActivationService;
        this.appUserRepository = appUserRepository;
        this.currentSessionService = currentSessionService;
        this.jwtRevocationService = jwtRevocationService;
    }

    @Override
    public ResponseEntity<LoginSuccessResponse> loginUser(
            @Valid @RequestBody LoginRequest loginRequest
    ) {

        LoginSuccessResponse response =
                authenticationService.login(loginRequest);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<SessionVerifyResponse> verifyCurrentSession() {

        // 1. Récupérer l'authentification courante

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        // 2. Vérifier que l'authentification contient un JWT

        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {

            throw new AuthenticationCredentialsNotFoundException(
                    "Aucune session JWT valide."
            );
        }

        // 3. Vérifier et reconstruire la session

        SessionVerifyResponse response =
                currentSessionService.verifySession(
                        jwtAuth.getToken()
                );

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<LogoutUser200Response> logoutUser() {

        // 1. Récupérer l'utilisateur authentifié

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        // 2. Vérifier la présence d'un JWT valide

        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {

            throw new AuthenticationCredentialsNotFoundException(
                    "Aucune session active."
            );
        }

        // 3. Révoquer le JWT dans PostgreSQL

        jwtRevocationService.revokeToken(
                jwtAuth.getToken()
        );

        // 4. Construire la réponse

        LogoutUser200Response response =
                new LogoutUser200Response();

        response.setSuccess(true);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<LoginSuccessResponse> activateAccount(
            @Valid @RequestBody ActivateAccountRequest request
    ) {

        // 1. Vérifier le token et activer le compte

        UUID userId = accountActivationService.activateAccount(
                request.getInvitationToken(),
                request.getNewPassword()
        );

        // 2. Récupérer l'employé activé

        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Utilisateur introuvable après activation."
                ));

        // 3. Préparer la connexion automatique

        LoginRequest loginRequest = new LoginRequest();

        loginRequest.setEmail(user.getEmail());

        loginRequest.setPassword(request.getNewPassword());

        // 4. Générer une session JWT

        LoginSuccessResponse response =
                authenticationService.login(loginRequest);

        // 5. Retourner les informations de session

        return ResponseEntity.ok(response);
    }
}
