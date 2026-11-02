package com.employeecare.api.auth.service;

import com.employeecare.api.auth.entity.AccountActivationToken;
import com.employeecare.api.auth.repository.AccountActivationTokenRepository;
import com.employeecare.api.user.entity.AccountStatus;
import com.employeecare.api.user.entity.AppUser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivationTokenServiceTest {

    @Mock
    private AccountActivationTokenRepository tokenRepository;

    private ActivationTokenService service;

    @BeforeEach
    void setUp() {
        service = new ActivationTokenService(tokenRepository);
    }

    @Test
    void generateToken_enregistreUniquementLeHashEtRetourneLeJetonBrut() {

        AppUser user = pendingUser();

        String rawToken = service.generateToken(user);

        ArgumentCaptor<AccountActivationToken> captor =
                ArgumentCaptor.forClass(AccountActivationToken.class);
        verify(tokenRepository).save(captor.capture());

        AccountActivationToken saved = captor.getValue();

        assertThat(rawToken).isNotBlank();
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getTokenHash())
                .hasSize(64)
                .matches("[0-9a-f]{64}")
                .isNotEqualTo(rawToken);
        assertThat(saved.getExpiresAt())
                .isAfter(Instant.now().plus(Duration.ofHours(23)))
                .isBefore(Instant.now().plus(Duration.ofHours(25)));
    }

    @Test
    void generateToken_produitDesJetonsDifferents() {

        AppUser user = pendingUser();

        String first = service.generateToken(user);
        String second = service.generateToken(user);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void generateToken_utilisateurNull_leveException() {

        assertThatThrownBy(() -> service.generateToken(null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(tokenRepository, never()).save(any());
    }

    @Test
    void generateToken_utilisateurSansId_leveException() {

        AppUser user = pendingUser();
        user.setId(null);

        assertThatThrownBy(() -> service.generateToken(user))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generateToken_compteDejaActif_leveException() {

        AppUser user = pendingUser();
        user.setStatus(AccountStatus.ACTIF);

        assertThatThrownBy(() -> service.generateToken(user))
                .isInstanceOf(IllegalArgumentException.class);

        verify(tokenRepository, never()).save(any());
    }

    @Test
    void findValidToken_jetonValide_retourneLeJeton() {

        AppUser user = pendingUser();
        String rawToken = service.generateToken(user);

        ArgumentCaptor<AccountActivationToken> captor =
                ArgumentCaptor.forClass(AccountActivationToken.class);
        verify(tokenRepository).save(captor.capture());
        AccountActivationToken saved = captor.getValue();

        when(tokenRepository.findByTokenHashAndUsedAtIsNullAndRevokedAtIsNull(
                saved.getTokenHash()
        )).thenReturn(Optional.of(saved));

        AccountActivationToken found = service.findValidToken(rawToken);

        assertThat(found).isSameAs(saved);
    }

    @Test
    void findValidToken_jetonVide_leveException() {

        assertThatThrownBy(() -> service.findValidToken("  "))
                .isInstanceOf(IllegalArgumentException.class);

        verify(tokenRepository, never())
                .findByTokenHashAndUsedAtIsNullAndRevokedAtIsNull(anyString());
    }

    @Test
    void findValidToken_jetonInconnu_leveException() {

        when(tokenRepository.findByTokenHashAndUsedAtIsNullAndRevokedAtIsNull(anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findValidToken("inconnu"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalide ou expiré");
    }

    @Test
    void findValidToken_jetonExpire_leveException() {

        AccountActivationToken expired = new AccountActivationToken(
                pendingUser(),
                "hash",
                Instant.now().minusSeconds(60)
        );

        when(tokenRepository.findByTokenHashAndUsedAtIsNullAndRevokedAtIsNull(anyString()))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.findValidToken("raw"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findValidToken_compteDejaActive_leveException() {

        AppUser user = pendingUser();
        user.setStatus(AccountStatus.ACTIF);

        AccountActivationToken token = new AccountActivationToken(
                user,
                "hash",
                Instant.now().plus(Duration.ofHours(1))
        );

        when(tokenRepository.findByTokenHashAndUsedAtIsNullAndRevokedAtIsNull(anyString()))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.findValidToken("raw"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static AppUser pendingUser() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setStatus(AccountStatus.EN_ATTENTE_ACTIVATION);
        return user;
    }
}
