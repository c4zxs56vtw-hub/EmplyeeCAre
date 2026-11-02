package com.employeecare.api.auth.controller;

import com.employeecare.api.auth.service.AccountActivationService;
import com.employeecare.api.auth.service.AuthenticationService;
import com.employeecare.api.auth.service.CurrentSessionService;
import com.employeecare.api.generated.models.ActivateAccountRequest;
import com.employeecare.api.generated.models.LoginRequest;
import com.employeecare.api.generated.models.LoginSuccessResponse;
import com.employeecare.api.generated.models.SessionVerifyResponse;
import com.employeecare.api.user.entity.AppUser;
import com.employeecare.api.user.repository.AppUserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationControllerTest {

    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private AccountActivationService accountActivationService;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private CurrentSessionService currentSessionService;

    private AuthenticationController controller;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        controller = new AuthenticationController(
                authenticationService,
                accountActivationService,
                appUserRepository,
                currentSessionService
        );
    }

    @Test
    void loginUser_retourneReponseOkAvecSession() {

        LoginRequest request = new LoginRequest();
        request.setEmail("claire.moreau@entreprise.com");
        request.setPassword("MotDePasseValide2026!");

        LoginSuccessResponse expectedResponse = new LoginSuccessResponse();
        expectedResponse.setSuccess(true);
        expectedResponse.setToken("jwt-token");
        expectedResponse.setRedirectPath(
                LoginSuccessResponse.RedirectPathEnum.fromValue("/employe")
        );

        when(authenticationService.login(request)).thenReturn(expectedResponse);

        ResponseEntity<LoginSuccessResponse> response =
                controller.loginUser(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expectedResponse);
        verify(authenticationService).login(request);
    }

    @Test
    void verifyCurrentSession_authentificationJwtPresente_retourneSessionVerifiee() {

        Jwt jwt = mock(Jwt.class);
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt, Collections.emptyList(), "user-id");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        SessionVerifyResponse expectedResponse = new SessionVerifyResponse();
        expectedResponse.setValid(true);

        when(currentSessionService.verifySession(jwt)).thenReturn(expectedResponse);

        ResponseEntity<SessionVerifyResponse> response = controller.verifyCurrentSession();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expectedResponse);
        verify(currentSessionService).verifySession(jwt);
    }

    @Test
    void verifyCurrentSession_aucuneAuthentificationJwt_leveAuthenticationCredentialsNotFoundException() {

        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> controller.verifyCurrentSession())
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
                .hasMessageContaining("Aucune session JWT valide.");

        verify(currentSessionService, never()).verifySession(any());
    }

    @Test
    void activateAccount_jetonValide_activeCompteEtOuvreSessionJwt() {

        UUID userId = UUID.randomUUID();
        ActivateAccountRequest request = new ActivateAccountRequest();
        request.setInvitationToken("inv-token-123");
        request.setNewPassword("MonNouveauMotDePasse2026!");

        AppUser activatedUser = new AppUser();
        activatedUser.setId(userId);
        activatedUser.setEmail("claire.moreau@entreprise.com");

        LoginSuccessResponse loginResponse = new LoginSuccessResponse();
        loginResponse.setSuccess(true);
        loginResponse.setToken("jwt-apres-activation");
        loginResponse.setRedirectPath(
                LoginSuccessResponse.RedirectPathEnum.fromValue("/employe")
        );

        when(accountActivationService.activateAccount(
                "inv-token-123",
                "MonNouveauMotDePasse2026!"
        )).thenReturn(userId);

        when(appUserRepository.findById(userId))
                .thenReturn(Optional.of(activatedUser));

        when(authenticationService.login(any(LoginRequest.class)))
                .thenReturn(loginResponse);

        ResponseEntity<LoginSuccessResponse> response =
                controller.activateAccount(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(loginResponse);

        ArgumentCaptor<LoginRequest> loginCaptor =
                ArgumentCaptor.forClass(LoginRequest.class);
        verify(authenticationService).login(loginCaptor.capture());

        LoginRequest capturedLogin = loginCaptor.getValue();
        assertThat(capturedLogin.getEmail())
                .isEqualTo("claire.moreau@entreprise.com");
        assertThat(capturedLogin.getPassword())
                .isEqualTo("MonNouveauMotDePasse2026!");
    }

    @Test
    void activateAccount_utilisateurIntrouvableApresActivation_leveIllegalStateException() {

        UUID userId = UUID.randomUUID();
        ActivateAccountRequest request = new ActivateAccountRequest();
        request.setInvitationToken("inv-token-123");
        request.setNewPassword("MonNouveauMotDePasse2026!");

        when(accountActivationService.activateAccount(any(), any()))
                .thenReturn(userId);
        when(appUserRepository.findById(userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.activateAccount(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Utilisateur introuvable après activation");

        verify(authenticationService, never()).login(any());
    }
}
