package com.employeecare.api.bootstrap;

import com.employeecare.api.user.entity.AccountStatus;
import com.employeecare.api.user.entity.AppUser;
import com.employeecare.api.user.entity.Role;

import com.employeecare.api.user.repository.AppUserRepository;
import com.employeecare.api.user.repository.RoleRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

@Component
@ConditionalOnProperty(
        name = "employeecare.bootstrap.admin.enabled",
        havingValue = "true"
)
public class InitialAdminBootstrap implements ApplicationRunner {

    private final AppUserRepository appUserRepository;

    private final RoleRepository roleRepository;

    private final PasswordEncoder passwordEncoder;

    private final String adminEmail;

    private final String adminPassword;

    public InitialAdminBootstrap(
            AppUserRepository appUserRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,

            @Value("${ADMIN_BOOTSTRAP_EMAIL:}")
            String adminEmail,

            @Value("${ADMIN_BOOTSTRAP_PASSWORD:}")
            String adminPassword
    ) {

        this.appUserRepository = appUserRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {

        // 1. Vérifier si un administrateur existe déjà

        if (appUserRepository.existsByRoles_Code("ADMINISTRATEUR")) {

            System.out.println(
                    "EmployeeCare : un administrateur existe déjà."
            );

            return;
        }

        // 2. Vérifier les informations du premier administrateur

        if (adminEmail == null
                || adminEmail.isBlank()
                || adminPassword == null
                || adminPassword.length() < 12
                || adminPassword.getBytes(
                        StandardCharsets.UTF_8
                   ).length > 72) {

            throw new IllegalStateException(
                    "Configurez ADMIN_BOOTSTRAP_EMAIL et "
                    + "ADMIN_BOOTSTRAP_PASSWORD "
                    + "(12 caractères minimum, 72 octets maximum)."
            );
        }

        String email = adminEmail
                .trim()
                .toLowerCase(Locale.ROOT);

        // 3. Vérifier l'unicité de l'email

        if (appUserRepository.existsByEmailIgnoreCase(email)) {

            throw new IllegalStateException(
                    "Cette adresse email est déjà utilisée."
            );
        }

        // 4. Récupérer le rôle ADMINISTRATEUR

        Role adminRole = roleRepository
                .findById("ADMINISTRATEUR")
                .orElseThrow(() -> new IllegalStateException(
                        "Le rôle ADMINISTRATEUR est introuvable."
                ));

        // 5. Créer le compte administrateur

        AppUser admin = new AppUser();

        admin.setFirstName("Administrateur");

        admin.setLastName("Initial");

        admin.setEmail(email);

        admin.setEmployeeNumber(
                "EC-" + UUID.randomUUID()
        );

        // 6. Protéger le mot de passe

        admin.setPasswordHash(
                passwordEncoder.encode(adminPassword)
        );

        // 7. Activer le compte

        admin.setStatus(AccountStatus.ACTIF);

        // 8. Attribuer le rôle administrateur

        admin.getRoles().add(adminRole);

        // 9. Enregistrer dans PostgreSQL

        appUserRepository.saveAndFlush(admin);

        System.out.println(
                "EmployeeCare : compte administrateur initial créé."
        );
    }
}
