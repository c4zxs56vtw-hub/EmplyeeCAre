package com.employeecare.api.auth.security;

import com.employeecare.api.auth.service.JwtRevocationService;
import com.employeecare.api.user.entity.AccountStatus;
import com.employeecare.api.user.entity.AppUser;
import com.employeecare.api.user.entity.Role;
import com.employeecare.api.user.repository.AppUserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

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
class DatabaseJwtAuthenticationConverterTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private JwtRevocationService jwtRevocationService;

    private DatabaseJwtAuthenticationConverter converter;

    @BeforeEach
    void setUp() {
        converter = new DatabaseJwtAuthenticationConverter(appUserRepository, jwtRevocationService);
    }

    @Test
    void convert_jetonRevoque_leveInvalidBearerToken() {

        UUID userId = UUID.randomUUID();
        Jwt jwt = jwt(userId.toString());

        when(jwtRevocationService.isRevoked(jwt)).thenReturn(true);

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(InvalidBearerTokenException.class);

        verify(appUserRepository, never()).findByIdWithRoles(any());
    }

    @Test
    void convert_compteActif_retourneLesRolesDepuisLaBase() {

        UUID userId = UUID.randomUUID();
        AppUser user = user(userId, AccountStatus.ACTIF, "EMPLOYE", "RESPONSABLE");

        when(appUserRepository.findByIdWithRoles(userId))
                .thenReturn(Optional.of(user));

        AbstractAuthenticationToken authentication =
                converter.convert(jwt(userId.toString()));

        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo(userId.toString());
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_EMPLOYE", "ROLE_RESPONSABLE");
    }

    @Test
    void convert_sessionVersionDifferente_leveInvalidBearerToken() {

        UUID userId = UUID.randomUUID();
        AppUser user = user(userId, AccountStatus.ACTIF, "EMPLOYE");
        user.setSessionVersion(2L);

        when(appUserRepository.findByIdWithRoles(userId))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> converter.convert(jwt(userId.toString(), 1L)))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void convert_sessionVersionAbsente_leveInvalidBearerToken() {

        UUID userId = UUID.randomUUID();
        AppUser user = user(userId, AccountStatus.ACTIF, "EMPLOYE");

        when(appUserRepository.findByIdWithRoles(userId))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> converter.convert(jwt(userId.toString(), null)))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void convert_subjectNonUuid_leveInvalidBearerToken() {

        assertThatThrownBy(() -> converter.convert(jwt("pas-un-uuid")))
                .isInstanceOf(InvalidBearerTokenException.class);

        verify(appUserRepository, never()).findByIdWithRoles(any());
    }

    @Test
    void convert_utilisateurIntrouvable_leveInvalidBearerToken() {

        UUID userId = UUID.randomUUID();

        when(appUserRepository.findByIdWithRoles(userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> converter.convert(jwt(userId.toString())))
                .isInstanceOf(InvalidBearerTokenException.class)
                .hasMessageContaining("introuvable");
    }

    @Test
    void convert_compteDesactive_leveInvalidBearerToken() {

        UUID userId = UUID.randomUUID();

        when(appUserRepository.findByIdWithRoles(userId))
                .thenReturn(Optional.of(
                        user(userId, AccountStatus.DESACTIVE, "EMPLOYE")
                ));

        assertThatThrownBy(() -> converter.convert(jwt(userId.toString())))
                .isInstanceOf(InvalidBearerTokenException.class)
                .hasMessageContaining("plus actif");
    }

    @Test
    void convert_compteEnAttenteActivation_leveInvalidBearerToken() {

        UUID userId = UUID.randomUUID();

        when(appUserRepository.findByIdWithRoles(userId))
                .thenReturn(Optional.of(
                        user(userId, AccountStatus.EN_ATTENTE_ACTIVATION, "EMPLOYE")
                ));

        assertThatThrownBy(() -> converter.convert(jwt(userId.toString())))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void convert_aucunRole_leveInvalidBearerToken() {

        UUID userId = UUID.randomUUID();

        when(appUserRepository.findByIdWithRoles(userId))
                .thenReturn(Optional.of(user(userId, AccountStatus.ACTIF)));

        assertThatThrownBy(() -> converter.convert(jwt(userId.toString())))
                .isInstanceOf(InvalidBearerTokenException.class)
                .hasMessageContaining("Aucune autorisation");
    }

    // ===== Utilitaires =====

    private static Jwt jwt(String subject) {
        return jwt(subject, 0L);
    }

    private static Jwt jwt(String subject, Long sessionVersion) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(subject);

        if (sessionVersion != null) {
            builder.claim("session_version", sessionVersion);
        }

        return builder.build();
    }

    private static AppUser user(UUID id, AccountStatus status, String... roleCodes) {

        AppUser user = new AppUser();
        user.setId(id);
        user.setStatus(status);
        user.setSessionVersion(0L);

        Set<Role> roles = new java.util.HashSet<>();
        for (String code : roleCodes) {
            Role role = new Role();
            role.setCode(code);
            roles.add(role);
        }
        user.setRoles(roles);

        return user;
    }
}
