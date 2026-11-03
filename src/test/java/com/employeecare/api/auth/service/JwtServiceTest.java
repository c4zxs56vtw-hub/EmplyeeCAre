package com.employeecare.api.auth.service;

import com.employeecare.api.user.entity.AccountStatus;
import com.employeecare.api.user.entity.AppUser;
import com.employeecare.api.user.entity.Role;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET_STRING =
            "cle-secrete-pour-les-tests-unitaires-de-32-octets-au-moins!";

    private static final String ISSUER = "http://localhost:8080/api";

    private final SecretKey key = new SecretKeySpec(
            SECRET_STRING.getBytes(StandardCharsets.UTF_8),
            "HmacSHA256"
    );

    private JwtService jwtService;

    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {

        NimbusJwtEncoder encoder = NimbusJwtEncoder
                .withSecretKey(key)
                .algorithm(MacAlgorithm.HS256)
                .build();

        jwtDecoder = NimbusJwtDecoder
                .withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        jwtService = new JwtService(encoder, ISSUER, Duration.ofMinutes(15));
    }

    @Test
    void generateAccessToken_contientSubjectIssuerRolesEtExpiration() {

        AppUser user = user(AccountStatus.ACTIF, "RESPONSABLE", "EMPLOYE");

        Instant before = Instant.now().minusSeconds(1);
        String token = jwtService.generateAccessToken(user);
        Jwt jwt = jwtDecoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getIssuer().toString()).isEqualTo(ISSUER);
        assertThat(jwt.getId()).isNotBlank();

        // Les rôles sont triés par ordre alphabétique
        assertThat(jwt.getClaimAsStringList("roles"))
                .containsExactly("EMPLOYE", "RESPONSABLE");

        assertThat((Long) jwt.getClaim("session_version")).isEqualTo(0L);

        assertThat(jwt.getIssuedAt()).isAfterOrEqualTo(before.truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
                .isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void generateAccessToken_chaqueJetonPossedeUnIdentifiantUnique() {

        AppUser user = user(AccountStatus.ACTIF, "EMPLOYE");

        Jwt first = jwtDecoder.decode(jwtService.generateAccessToken(user));
        Jwt second = jwtDecoder.decode(jwtService.generateAccessToken(user));

        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    @Test
    void generateAccessToken_compteNonActif_leveIllegalArgument() {

        AppUser enAttente = user(AccountStatus.EN_ATTENTE_ACTIVATION, "EMPLOYE");
        AppUser desactive = user(AccountStatus.DESACTIVE, "EMPLOYE");

        assertThatThrownBy(() -> jwtService.generateAccessToken(enAttente))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> jwtService.generateAccessToken(desactive))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generateAccessToken_utilisateurSansRole_leveIllegalState() {

        AppUser sansRole = user(AccountStatus.ACTIF);

        assertThatThrownBy(() -> jwtService.generateAccessToken(sansRole))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void generateAccessToken_utilisateurInvalide_leveIllegalArgument() {

        AppUser sansId = new AppUser();
        sansId.setStatus(AccountStatus.ACTIF);

        assertThatThrownBy(() -> jwtService.generateAccessToken(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> jwtService.generateAccessToken(sansId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructeur_dureeNegativeOuNulle_leveIllegalArgument() {

        NimbusJwtEncoder encoder = NimbusJwtEncoder
                .withSecretKey(key)
                .algorithm(MacAlgorithm.HS256)
                .build();

        assertThatThrownBy(() -> new JwtService(encoder, ISSUER, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new JwtService(encoder, ISSUER, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ===== Utilitaires =====

    private static AppUser user(AccountStatus status, String... roleCodes) {

        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setStatus(status);

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
