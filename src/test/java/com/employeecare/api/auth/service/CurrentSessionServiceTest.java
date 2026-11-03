package com.employeecare.api.auth.service;

import com.employeecare.api.department.entity.Department;
import com.employeecare.api.generated.models.AuthSession;
import com.employeecare.api.generated.models.SessionVerifyResponse;
import com.employeecare.api.generated.models.UserRole;
import com.employeecare.api.user.entity.AppUser;
import com.employeecare.api.user.entity.Role;
import com.employeecare.api.user.repository.AppUserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentSessionServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    private CurrentSessionService service;

    private final Instant issuedAt = Instant.parse("2026-11-02T10:00:00Z");
    private final Instant expiresAt = Instant.parse("2026-11-02T10:15:00Z");

    @BeforeEach
    void setUp() {
        service = new CurrentSessionService(appUserRepository);
    }

    @Test
    void verifySession_administrateur_retourneSessionValide() {

        UUID userId = UUID.randomUUID();
        AppUser admin = user(userId, "ADMINISTRATEUR");

        when(appUserRepository.findByIdWithRoles(userId))
                .thenReturn(Optional.of(admin));

        SessionVerifyResponse response = service.verifySession(jwt(userId.toString()));

        assertThat(response.getValid()).isTrue();

        AuthSession session = response.getSession();
        assertThat(session.getUserId()).isEqualTo(userId.toString());
        assertThat(session.getFullName()).isEqualTo("Sophie Dubois");
        assertThat(session.getEmail()).isEqualTo("sophie.dubois@entreprise.com");
        assertThat(session.getRoles()).containsExactly(UserRole.ADMINISTRATEUR);
        assertThat(session.getRoleLabel()).isEqualTo("Administrateur");
        assertThat(session.getIssuedAt()).isEqualTo(issuedAt.toEpochMilli());
        assertThat(session.getExpiresAt()).isEqualTo(expiresAt.toEpochMilli());
        assertThat(session.getEmployeeId()).isNull();
        assertThat(session.getDepartmentName()).isNull();
    }

    @Test
    void verifySession_responsable_renseigneLibelleEmployeIdEtDepartement() {

        UUID userId = UUID.randomUUID();
        AppUser manager = user(userId, "EMPLOYE", "RESPONSABLE");

        Department department = new Department();
        department.setName("Administration");
        manager.setDepartment(department);

        when(appUserRepository.findByIdWithRoles(userId))
                .thenReturn(Optional.of(manager));

        AuthSession session = service.verifySession(jwt(userId.toString())).getSession();

        assertThat(session.getRoleLabel()).isEqualTo("Responsable");
        assertThat(session.getRoles())
                .containsExactlyInAnyOrder(UserRole.EMPLOYE, UserRole.RESPONSABLE);
        assertThat(session.getEmployeeId()).isEqualTo(userId.toString());
        assertThat(session.getDepartmentName()).isEqualTo("Administration");
    }

    @Test
    void verifySession_employe_libelleEmploye() {

        UUID userId = UUID.randomUUID();

        when(appUserRepository.findByIdWithRoles(userId))
                .thenReturn(Optional.of(user(userId, "EMPLOYE")));

        AuthSession session = service.verifySession(jwt(userId.toString())).getSession();

        assertThat(session.getRoleLabel()).isEqualTo("Employé");
        assertThat(session.getEmployeeId()).isEqualTo(userId.toString());
    }

    @Test
    void verifySession_subjectNonUuid_leveBadCredentials() {

        assertThatThrownBy(() -> service.verifySession(jwt("pas-un-uuid")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Session invalide");

        verify(appUserRepository, never()).findByIdWithRoles(any());
    }

    @Test
    void verifySession_utilisateurIntrouvable_leveBadCredentials() {

        UUID userId = UUID.randomUUID();

        when(appUserRepository.findByIdWithRoles(userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifySession(jwt(userId.toString())))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("introuvable");
    }

    // ===== Utilitaires =====

    private Jwt jwt(String subject) {
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
    }

    private static AppUser user(UUID id, String... roleCodes) {

        AppUser user = new AppUser();
        user.setId(id);
        user.setFirstName("Sophie");
        user.setLastName("Dubois");
        user.setEmail("sophie.dubois@entreprise.com");

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
