package com.employeecare.api.bootstrap;

import com.employeecare.api.user.entity.AccountStatus;
import com.employeecare.api.user.entity.AppUser;
import com.employeecare.api.user.entity.Role;
import com.employeecare.api.user.repository.AppUserRepository;
import com.employeecare.api.user.repository.RoleRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InitialAdminBootstrapTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void run_aucunAdminExistant_creeCompteAdminActif() {

        InitialAdminBootstrap bootstrap = new InitialAdminBootstrap(
                appUserRepository,
                roleRepository,
                passwordEncoder,
                "  Admin@Entreprise.com ",
                "AdminPassword2026!"
        );

        Role adminRole = new Role();
        adminRole.setCode("ADMINISTRATEUR");

        when(appUserRepository.existsByRoles_Code("ADMINISTRATEUR"))
                .thenReturn(false);
        when(appUserRepository.existsByEmailIgnoreCase("admin@entreprise.com"))
                .thenReturn(false);
        when(roleRepository.findById("ADMINISTRATEUR"))
                .thenReturn(Optional.of(adminRole));
        when(passwordEncoder.encode("AdminPassword2026!"))
                .thenReturn("encoded-admin-pwd");

        bootstrap.run(new DefaultApplicationArguments());

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).saveAndFlush(captor.capture());

        AppUser saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("admin@entreprise.com");
        assertThat(saved.getPasswordHash()).isEqualTo("encoded-admin-pwd");
        assertThat(saved.getStatus()).isEqualTo(AccountStatus.ACTIF);
        assertThat(saved.getRoles())
                .extracting(Role::getCode)
                .containsExactly("ADMINISTRATEUR");
    }

    @Test
    void run_adminExisteDeja_neCreeRien() {

        InitialAdminBootstrap bootstrap = new InitialAdminBootstrap(
                appUserRepository,
                roleRepository,
                passwordEncoder,
                "admin@entreprise.com",
                "AdminPassword2026!"
        );

        when(appUserRepository.existsByRoles_Code("ADMINISTRATEUR"))
                .thenReturn(true);

        bootstrap.run(new DefaultApplicationArguments());

        verify(appUserRepository, never()).saveAndFlush(any());
    }

    @Test
    void run_motDePasseMoinsDe12Caracteres_leveIllegalStateException() {

        InitialAdminBootstrap bootstrap = new InitialAdminBootstrap(
                appUserRepository,
                roleRepository,
                passwordEncoder,
                "admin@entreprise.com",
                "Court123!"
        );

        when(appUserRepository.existsByRoles_Code("ADMINISTRATEUR"))
                .thenReturn(false);

        assertThatThrownBy(() -> bootstrap.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("12 caractères minimum");

        verify(appUserRepository, never()).saveAndFlush(any());
    }
}
