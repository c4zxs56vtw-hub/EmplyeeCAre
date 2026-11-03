package com.employeecare.api.admin.service;

import com.employeecare.api.generated.models.CreateEmployeeRequest;
import com.employeecare.api.generated.models.EmployeeAccount;
import com.employeecare.api.generated.models.UpdateEmployeeStatusRequest;

import com.employeecare.api.user.entity.AccountStatus;
import com.employeecare.api.user.entity.AppUser;
import com.employeecare.api.user.mapper.EmployeeAccountResponseMapper;
import com.employeecare.api.user.repository.AppUserRepository;
import com.employeecare.api.user.service.EmployeeService;

import jakarta.persistence.EntityManager;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class AdminEmployeeApplicationService {

    private final EmployeeService employeeService;

    private final EmployeeAccountResponseMapper responseMapper;

    private final EntityManager entityManager;

    private final AppUserRepository appUserRepository;

    public AdminEmployeeApplicationService(
            EmployeeService employeeService,
            EmployeeAccountResponseMapper responseMapper,
            EntityManager entityManager,
            AppUserRepository appUserRepository
    ) {
        this.employeeService = employeeService;
        this.responseMapper = responseMapper;
        this.entityManager = entityManager;
        this.appUserRepository = appUserRepository;
    }

    @Transactional
    public EmployeeAccount createEmployee(
            CreateEmployeeRequest request
    ) {

        AppUser employee = employeeService.createEmployee(request);

        // Synchroniser les changements avec PostgreSQL.
        // Cela permet notamment de renseigner createdAt.
        entityManager.flush();

        return responseMapper.toResponse(employee);
    }

    @Transactional(readOnly = true)
    public List<EmployeeAccount> listEmployees() {

        return appUserRepository
                .findAllByOrderByRolesDesc()
                .stream()
                .filter(user -> user.getRoles()
                        .stream()
                        .anyMatch(role ->
                                "EMPLOYE".equals(role.getCode())))
                .map(responseMapper::toResponse)
                .toList();
    }

    @Transactional
    public EmployeeAccount updateEmployeeStatus(
            String employeeId,
            UpdateEmployeeStatusRequest request
    ) {

        // 1. Vérifier les informations reçues

        if (request == null || request.getStatus() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Le nouveau statut est obligatoire."
            );
        }

        // 2. Vérifier l'identifiant

        UUID userId;

        try {
            userId = UUID.fromString(employeeId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Identifiant utilisateur invalide."
            );
        }

        // 3. Rechercher l'utilisateur

        AppUser user = appUserRepository
                .findByIdWithRoles(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Utilisateur introuvable."
                ));

        // 4. Protéger les comptes administrateurs

        boolean isAdministrator = user.getRoles()
                .stream()
                .anyMatch(role ->
                        "ADMINISTRATEUR".equals(role.getCode())
                );

        if (isAdministrator) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Cette opération ne peut pas modifier un compte administrateur."
            );
        }

        // 5. Déterminer le nouveau statut

        AccountStatus newStatus = AccountStatus.valueOf(
                request.getStatus().name()
        );

        // 6. Empêcher l'activation sans invitation

        if (user.getStatus() == AccountStatus.EN_ATTENTE_ACTIVATION) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ce compte doit d'abord terminer son activation par email."
            );
        }

        if (newStatus == AccountStatus.EN_ATTENTE_ACTIVATION) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ce statut ne peut pas être défini manuellement."
            );
        }

        // 7. Modifier le statut

        if (user.getStatus() != newStatus) {

            user.setStatus(newStatus);

            // Invalider toutes les anciennes sessions JWT
            user.setSessionVersion(
                    user.getSessionVersion() + 1
            );
        }

        // 8. Retourner le compte actualisé

        return responseMapper.toResponse(user);
    }
}
