package com.employeecare.api.user.service;

import com.employeecare.api.auth.service.ActivationTokenService;
import com.employeecare.api.department.entity.Department;
import com.employeecare.api.department.repository.DepartmentRepository;

import com.employeecare.api.generated.models.CreateEmployeeRequest;
import com.employeecare.api.generated.models.UserRole;

import com.employeecare.api.notification.email.AccountInvitationRequestedEvent;
import com.employeecare.api.user.entity.AccountStatus;
import com.employeecare.api.user.entity.AppUser;
import com.employeecare.api.user.entity.Role;

import com.employeecare.api.user.mapper.AppUserMapper;
import com.employeecare.api.user.repository.AppUserRepository;
import com.employeecare.api.user.repository.RoleRepository;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class EmployeeService {

    private final AppUserRepository appUserRepository;

    private final DepartmentRepository departmentRepository;

    private final RoleRepository roleRepository;

    private final AppUserMapper appUserMapper;

    private final ActivationTokenService activationTokenService;

    private final ApplicationEventPublisher eventPublisher;

    public EmployeeService(
            AppUserRepository appUserRepository,
            DepartmentRepository departmentRepository,
            RoleRepository roleRepository,
            AppUserMapper appUserMapper,
            ActivationTokenService activationTokenService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.appUserRepository = appUserRepository;
        this.departmentRepository = departmentRepository;
        this.roleRepository = roleRepository;
        this.appUserMapper = appUserMapper;
        this.activationTokenService = activationTokenService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AppUser createEmployee(CreateEmployeeRequest request) {

        // 1. Vérifier les informations obligatoires

        if (request == null
                || !StringUtils.hasText(request.getFirstName())
                || !StringUtils.hasText(request.getLastName())
                || !StringUtils.hasText(request.getEmail())
                || !StringUtils.hasText(request.getPosition())
                || request.getDepartmentId() == null
                || request.getRole() == null) {

            throw new IllegalArgumentException(
                    "Les informations obligatoires sont manquantes."
            );
        }

        // 2. Normaliser l'adresse email

        String email = request.getEmail()
                .trim()
                .toLowerCase(Locale.ROOT);

        // 3. Vérifier que l'email n'existe pas déjà

        if (appUserRepository.existsByEmailIgnoreCase(email)) {

            throw new IllegalArgumentException(
                    "Cette adresse email est déjà utilisée."
            );
        }

        // 4. Vérifier le rôle demandé

        if (request.getRole() == UserRole.ADMINISTRATEUR) {

            throw new IllegalArgumentException(
                    "Ce service ne permet pas de créer un administrateur."
            );
        }

        // Les catégories et habilitations confidentielles
        // seront gérées par un module dédié.

        if ((request.getAssignedCategories() != null
                && !request.getAssignedCategories().isEmpty())
                || Boolean.TRUE.equals(request.getIsConfidentialHab())) {

            throw new IllegalArgumentException(
                    "La gestion des catégories et habilitations "
                            + "confidentielles n'est pas encore disponible."
            );
        }

        // 5. Convertir l'identifiant du département

        UUID departmentId;

        try {

            departmentId = UUID.fromString(
                    request.getDepartmentId()
            );

        } catch (IllegalArgumentException exception) {

            throw new IllegalArgumentException(
                    "L'identifiant du département est invalide.",
                    exception
            );
        }

        // 6. Rechercher le département

        Department department = departmentRepository
                .findById(departmentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Le département demandé n'existe pas."
                ));

        if (!department.isActive()) {

            throw new IllegalArgumentException(
                    "Le département sélectionné est désactivé."
            );
        }

        // 7. Récupérer le rôle EMPLOYE

        Role employeeRole = roleRepository
                .findById("EMPLOYE")
                .orElseThrow(() -> new IllegalStateException(
                        "Le rôle EMPLOYE est introuvable."
                ));

        Set<Role> roles = new HashSet<>();

        roles.add(employeeRole);

        // 8. Ajouter RESPONSABLE si nécessaire

        if (request.getRole() == UserRole.RESPONSABLE) {

            Role managerRole = roleRepository
                    .findById("RESPONSABLE")
                    .orElseThrow(() -> new IllegalStateException(
                            "Le rôle RESPONSABLE est introuvable."
                    ));

            roles.add(managerRole);
        }

        // 9. Convertir le DTO en entité JPA

        AppUser employee = appUserMapper.toEntity(request);

        // 10. Compléter les informations métier

        employee.setEmail(email);

        employee.setDepartment(department);

        employee.setRoles(roles);

        employee.setStatus(
                AccountStatus.EN_ATTENTE_ACTIVATION
        );

        // Identifiant provisoire unique du collaborateur

        employee.setEmployeeNumber(
                "EC-" + UUID.randomUUID()
        );

        // 1. Enregistrer l'employé

        AppUser savedEmployee =
                appUserRepository.save(employee);


        // 2. Générer et enregistrer le jeton d'activation

        String activationToken =
                activationTokenService.generateToken(savedEmployee);


        // 3. Publier l'événement d'invitation

        eventPublisher.publishEvent(
                new AccountInvitationRequestedEvent(
                        savedEmployee.getEmail(),
                        savedEmployee.getFirstName(),
                        activationToken
                )
        );


        // 4. Retourner l'employé créé

        return savedEmployee;
    }
}