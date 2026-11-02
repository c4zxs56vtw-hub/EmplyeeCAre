package com.employeecare.api.auth.service;

import com.employeecare.api.auth.entity.AccountActivationToken;
import com.employeecare.api.user.entity.AccountStatus;
import com.employeecare.api.user.entity.AppUser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountActivationServiceTest {

    @Mock
    private ActivationTokenService activationTokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AccountActivationService service;

    @BeforeEach
    void setUp() {
        service = new AccountActivationService(
                activationTokenService,
                passwordEncoder
        );
    }

    @Test
    void activateAccount_donneesValides_encodeMotDePasseActiveCompteEtMarqueJetonUtilise() {

        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(userId);
        user.setStatus(AccountStatus.EN_ATTENTE_ACTIVATION);

        AccountActivationToken token = new AccountActivationToken(
                user,
                "hash-123",
                Instant.now().plus(Duration.ofHours(12))
        );

        when(activationTokenService.findValidToken("raw-token"))
                .thenReturn(token);
        when(passwordEncoder.encode("MonMotDePasseSecurise!"))
                .thenReturn("bcrypt-hash");

        UUID resultId = service.activateAccount(
                "raw-token",
                "MonMotDePasseSecurise!"
        );

        assertThat(resultId).isEqualTo(userId);
        assertThat(user.getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(user.getStatus()).isEqualTo(AccountStatus.ACTIF);
        assertThat(token.getUsedAt()).isNotNull();
    }

    @Test
    void activateAccount_motDePasseTropCourt_leveIllegalArgumentException() {

        assertThatThrownBy(() -> service.activateAccount("raw-token", "court"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("au moins 8 caractères");

        verify(activationTokenService, never()).findValidToken(any());
    }

    @Test
    void activateAccount_motDePasseDepassant72Octets_leveIllegalArgumentException() {

        String tooLong = "a".repeat(73);

        assertThatThrownBy(() -> service.activateAccount("raw-token", tooLong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trop long");

        verify(activationTokenService, never()).findValidToken(any());
    }

    @Test
    void activateAccount_compteNonEnAttenteActivation_leveIllegalStateException() {

        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setStatus(AccountStatus.ACTIF);

        AccountActivationToken token = new AccountActivationToken(
                user,
                "hash-123",
                Instant.now().plus(Duration.ofHours(12))
        );

        when(activationTokenService.findValidToken("raw-token"))
                .thenReturn(token);

        assertThatThrownBy(() ->
                service.activateAccount("raw-token", "MonMotDePasseSecurise!")
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ne peut pas être activé");
    }
}
