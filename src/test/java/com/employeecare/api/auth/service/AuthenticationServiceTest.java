package com.employeecare.api.auth.service;

import com.employeecare.api.department.entity.Department;
import com.employeecare.api.generated.models.LoginRequest;
import com.employeecare.api.generated.models.LoginSuccessResponse;
import com.employeecare.api.generated.models.UserRole;
import com.employeecare.api.user.entity.AccountStatus;
import com.employeecare.api.user.entity.AppUser;
import com.employeecare.api.user.entity.Role;
import com.employeecare.api.user.repository.AppUserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    private static final String EMAIL = "paul.martin@entreprise.com";
    private static final String PASSWORD = "Employe2026!";
    private static final String HASH = "bcrypt-hash";

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private JwtDecoder jwtDecoder;

    private AuthenticationService service;

    private final Instant issuedAt = Instant.parse("2026-11-02T10:00:00Z");
    private final Instant expiresAt = Instant.parse("2026-11-02T10:15:00Z");

    @BeforeEach
    void setUp() {
        service = new AuthenticationService(
                appUserRepository,
                passwordEncoder,
                jwtService,
                jwtDecoder
        );
    }

    @Test
    void login_employeActif_retourneJwtEtRedirectionEmploye() {

        AppUser user = activeUser("EMPLOYE");
        Department department = new Department();
        department.setName("Administration");
        user.setDepartment(department);

        stubSuccessfulLogin(user);

        LoginSuccessResponse response = service.login(request("  Paul.Martin@Entreprise.com ", PASSWORD));

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getRedirectPath().getValue()).isEqualTo("/employe");
        assertThat(response.getSession().getRoleLabel()).isEqualTo("Employé");
        assertThat(response.getSession().getRoles()).containsExactly(UserRole.EMPLOYE);
        assertThat(response.getSession().getEmployeeId()).isEqualTo(user.getId().toString());
        assertThat(response.getSession().getDepartmentName()).isEqualTo("Administration");
        assertThat(response.getSession().getIssuedAt()).isEqualTo(issuedAt.toEpochMilli());
        assertThat(response.getSession().getExpiresAt()).isEqualTo(expiresAt.toEpochMilli());

        // L'email est normalisé avant la recherche
        verify(appUserRepository).findByEmailIgnoreCase(EMAIL);
    }

    @Test
    void login_responsable_redirigeVersEspaceResponsable() {

        stubSuccessfulLogin(activeUser("EMPLOYE", "RESPONSABLE"));

        LoginSuccessResponse response = service.login(request(EMAIL, PASSWORD));

        assertThat(response.getRedirectPath().getValue()).isEqualTo("/responsable");
        assertThat(response.getSession().getRoleLabel()).isEqualTo("Responsable");
    }

    @Test
    void login_administrateur_redirigeVersEspaceAdmin() {

        AppUser admin = activeUser("ADMINISTRATEUR");
        stubSuccessfulLogin(admin);

        LoginSuccessResponse response = service.login(request(EMAIL, PASSWORD));

        assertThat(response.getRedirectPath().getValue()).isEqualTo("/admin");
        assertThat(response.getSession().getRoleLabel()).isEqualTo("Administrateur");
        assertThat(response.getSession().getEmployeeId()).isNull();
    }

    @Test
    void login_champsVides_leveIllegalArgument() {

        assertThatThrownBy(() -> service.login(request(" ", PASSWORD)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.login(request(EMAIL, "")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.login(null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(appUserRepository, never()).findByEmailIgnoreCase(anyString());
    }

    @Test
    void login_emailInconnu_leveBadCredentials() {

        when(appUserRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(request(EMAIL, PASSWORD)))
                .isInstanceOf(BadCredentialsException.class);

        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    void login_motDePasseIncorrect_leveBadCredentials() {

        AppUser user = activeUser("EMPLOYE");

        when(appUserRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("mauvais", HASH)).thenReturn(false);

        assertThatThrownBy(() -> service.login(request(EMAIL, "mauvais")))
                .isInstanceOf(BadCredentialsException.class);

        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    void login_compteSansMotDePasse_leveBadCredentials() {

        AppUser user = activeUser("EMPLOYE");
        user.setPasswordHash(null);
        user.setStatus(AccountStatus.EN_ATTENTE_ACTIVATION);

        when(appUserRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(request(EMAIL, PASSWORD)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_compteDesactive_leveDisabledException() {

        AppUser user = activeUser("EMPLOYE");
        user.setStatus(AccountStatus.DESACTIVE);

        when(appUserRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);

        assertThatThrownBy(() -> service.login(request(EMAIL, PASSWORD)))
                .isInstanceOf(DisabledException.class);

        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    void login_compteEnAttenteActivation_leveBadCredentials() {

        AppUser user = activeUser("EMPLOYE");
        user.setStatus(AccountStatus.EN_ATTENTE_ACTIVATION);

        when(appUserRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);

        assertThatThrownBy(() -> service.login(request(EMAIL, PASSWORD)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_aucunRole_leveIllegalState() {

        AppUser user = activeUser();

        when(appUserRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);

        assertThatThrownBy(() -> service.login(request(EMAIL, PASSWORD)))
                .isInstanceOf(IllegalStateException.class);
    }

    // ===== Utilitaires =====

    private void stubSuccessfulLogin(AppUser user) {

        Jwt decoded = Jwt.withTokenValue("jwt-token")
                .header("alg", "HS256")
                .subject(user.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();

        when(appUserRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(jwtService.generateAccessToken(user)).thenReturn("jwt-token");
        when(jwtDecoder.decode("jwt-token")).thenReturn(decoded);
    }

    private static LoginRequest request(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    private static AppUser activeUser(String... roleCodes) {

        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setFirstName("Paul");
        user.setLastName("Martin");
        user.setEmail(EMAIL);
        user.setPasswordHash(HASH);
        user.setStatus(AccountStatus.ACTIF);

        Set<Role> roles = new HashSet<>();
        for (String code : roleCodes) {
            Role role = new Role();
            role.setCode(code);
            roles.add(role);
        }
        user.setRoles(roles);

        return user;
    }
}
