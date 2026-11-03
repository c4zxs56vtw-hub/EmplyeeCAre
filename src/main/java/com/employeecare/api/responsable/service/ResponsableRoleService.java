package com.employeecare.api.responsable.service;

import com.employeecare.api.department.entity.Department;
import com.employeecare.api.generated.models.EmployeeAccount;
import com.employeecare.api.generated.models.UpdateRoleRequest;
import com.employeecare.api.responsable.entity.ResponsableAccount;
import com.employeecare.api.responsable.repository.ResponsableAccountRepository;
import com.employeecare.api.ticket.repository.TicketCategoryRepository;
import com.employeecare.api.user.entity.AccountStatus;
import com.employeecare.api.user.entity.AppUser;
import com.employeecare.api.user.entity.Role;
import com.employeecare.api.user.mapper.EmployeeAccountResponseMapper;
import com.employeecare.api.user.repository.AppUserRepository;
import com.employeecare.api.user.repository.RoleRepository;

import jakarta.persistence.EntityManager;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ResponsableRoleService {

    private final AppUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ResponsableAccountRepository responsableRepository;
    private final EmployeeAccountResponseMapper responseMapper;
    private final EntityManager entityManager;
    private final TicketCategoryRepository ticketCategoryRepository;

    public ResponsableRoleService(
            AppUserRepository userRepository,
            RoleRepository roleRepository,
            ResponsableAccountRepository responsableRepository,
            EmployeeAccountResponseMapper responseMapper,
            EntityManager entityManager,
            TicketCategoryRepository ticketCategoryRepository
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.responsableRepository = responsableRepository;
        this.responseMapper = responseMapper;
        this.entityManager = entityManager;
        this.ticketCategoryRepository = ticketCategoryRepository;
    }

    @Transactional
    public EmployeeAccount updateEmployeeRole(
            String employeeId,
            UpdateRoleRequest request
    ) {

        if (request == null || request.getAction() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "L'action est obligatoire."
            );
        }

        UUID userId = parseUuid(employeeId);

        AppUser user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Employé introuvable."
                ));

        boolean isAdmin = hasRole(user, "ADMINISTRATEUR");

        if (isAdmin) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Impossible de modifier les rôles d'un administrateur."
            );
        }

        if (!hasRole(user, "EMPLOYE")) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cet utilisateur n'est pas un employé."
            );
        }

        switch (request.getAction().toString()) {

            case "PROMOTE_TO_RESPONSABLE" ->
                    promote(user, request);

            case "REVOKE_RESPONSABLE" ->
                    revoke(user);

            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Action non reconnue."
            );
        }

        // Invalider les anciennes sessions JWT.
        user.setSessionVersion(
                user.getSessionVersion() + 1
        );

        entityManager.flush();

        return responseMapper.toResponse(user);
    }

    private void promote(
            AppUser user,
            UpdateRoleRequest request
    ) {

        if (user.getStatus() != AccountStatus.ACTIF) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Le compte doit être actif pour être promu."
            );
        }

        // 1. Vérifier le département.

        UUID departmentId = parseUuid(
                request.getDepartmentId()
        );

        Department department = entityManager.find(
                Department.class,
                departmentId
        );

        if (department == null || !department.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Département introuvable ou inactif."
            );
        }

        // 2. Récupérer le rôle RESPONSABLE.

        Role responsableRole = roleRepository
                .findById("RESPONSABLE")
                .orElseThrow(() -> new IllegalStateException(
                        "Le rôle RESPONSABLE est introuvable."
                ));

        // 3. Ajouter le rôle sans retirer EMPLOYE.

        if (!hasRole(user, "RESPONSABLE")) {
            user.getRoles().add(responsableRole);
        }

        // 4. Créer ou réactiver le profil responsable.

        ResponsableAccount account = responsableRepository
                .findById(user.getId())
                .orElseGet(() -> {
                    ResponsableAccount created =
                            new ResponsableAccount();

                    created.setUser(user);

                    return created;
                });

        account.setDepartment(department);
        account.setActive(true);

        account.setConfidentialHabilitation(
                Boolean.TRUE.equals(
                        request.getIsConfidentialHab()
                )
        );

        // 5. Préparer les catégories attribuées.

        Set<String> categories = new LinkedHashSet<>();

        if (request.getAssignedCategories() != null) {

            for (String category : request.getAssignedCategories()) {

                if (category == null || category.isBlank()) {

                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Le code de catégorie est obligatoire."
                    );
                }

                String categoryCode = category
                        .trim()
                        .toUpperCase(Locale.ROOT);

                boolean categoryExists =
                        ticketCategoryRepository
                                .existsByCodeAndActiveTrue(categoryCode);

                if (!categoryExists) {

                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Catégorie inexistante ou désactivée : "
                                    + categoryCode
                    );
                }

                categories.add(categoryCode);
            }
        }

        account.getAssignedCategories().clear();
        account.getAssignedCategories().addAll(categories);

        responsableRepository.save(account);
    }

    private void revoke(AppUser user) {

        // Retirer uniquement le rôle RESPONSABLE.

        user.getRoles().removeIf(
                role -> "RESPONSABLE".equals(role.getCode())
        );

        // Désactiver son profil responsable.

        responsableRepository.findById(user.getId())
                .ifPresent(account -> {

                    account.setActive(false);

                    account.setConfidentialHabilitation(false);

                    account.getAssignedCategories().clear();
                });
    }

    private boolean hasRole(AppUser user, String roleCode) {

        return user.getRoles()
                .stream()
                .anyMatch(role ->
                        roleCode.equals(role.getCode())
                );
    }

    private UUID parseUuid(String value) {

        try {
            return UUID.fromString(value);

        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Identifiant UUID invalide."
            );
        }
    }
}
