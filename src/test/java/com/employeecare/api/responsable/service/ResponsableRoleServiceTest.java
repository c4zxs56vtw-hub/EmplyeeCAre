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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResponsableRoleServiceTest {

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private ResponsableAccountRepository responsableRepository;

    @Mock
    private EmployeeAccountResponseMapper responseMapper;

    @Mock
    private EntityManager entityManager;

    @Mock
    private TicketCategoryRepository ticketCategoryRepository;

    private ResponsableRoleService service;

    @BeforeEach
    void setUp() {
        service = new ResponsableRoleService(
                userRepository,
                roleRepository,
                responsableRepository,
                responseMapper,
                entityManager,
                ticketCategoryRepository
        );
    }

    @Test
    void updateEmployeeRole_requeteOuActionNulle_leveBadRequest() {
        assertThatThrownBy(() -> service.updateEmployeeRole(UUID.randomUUID().toString(), null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        UpdateRoleRequest requestSansAction = new UpdateRoleRequest();
        assertThatThrownBy(() -> service.updateEmployeeRole(UUID.randomUUID().toString(), requestSansAction))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void updateEmployeeRole_uuidInvalide_leveBadRequest() {
        UpdateRoleRequest request = new UpdateRoleRequest();
        request.setAction(UpdateRoleRequest.ActionEnum.REVOKE_RESPONSABLE);

        assertThatThrownBy(() -> service.updateEmployeeRole("uuid-invalide", request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void updateEmployeeRole_utilisateurIntrouvable_leveNotFound() {
        UUID userId = UUID.randomUUID();
        UpdateRoleRequest request = new UpdateRoleRequest();
        request.setAction(UpdateRoleRequest.ActionEnum.REVOKE_RESPONSABLE);

        when(userRepository.findByIdWithRoles(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateEmployeeRole(userId.toString(), request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateEmployeeRole_administrateur_leveForbidden() {
        UUID userId = UUID.randomUUID();
        UpdateRoleRequest request = new UpdateRoleRequest();
        request.setAction(UpdateRoleRequest.ActionEnum.REVOKE_RESPONSABLE);

        AppUser admin = user(userId, AccountStatus.ACTIF, "ADMINISTRATEUR", "EMPLOYE");
        when(userRepository.findByIdWithRoles(userId)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.updateEmployeeRole(userId.toString(), request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void updateEmployeeRole_nonEmploye_leveConflict() {
        UUID userId = UUID.randomUUID();
        UpdateRoleRequest request = new UpdateRoleRequest();
        request.setAction(UpdateRoleRequest.ActionEnum.REVOKE_RESPONSABLE);

        AppUser user = user(userId, AccountStatus.ACTIF); // aucun rôle
        when(userRepository.findByIdWithRoles(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.updateEmployeeRole(userId.toString(), request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void promote_compteNonActif_leveConflict() {
        UUID userId = UUID.randomUUID();
        UpdateRoleRequest request = new UpdateRoleRequest();
        request.setAction(UpdateRoleRequest.ActionEnum.PROMOTE_TO_RESPONSABLE);
        request.setDepartmentId(UUID.randomUUID().toString());

        AppUser user = user(userId, AccountStatus.EN_ATTENTE_ACTIVATION, "EMPLOYE");
        when(userRepository.findByIdWithRoles(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.updateEmployeeRole(userId.toString(), request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void promote_departementInvalideOuInactif_leveBadRequest() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UpdateRoleRequest request = new UpdateRoleRequest();
        request.setAction(UpdateRoleRequest.ActionEnum.PROMOTE_TO_RESPONSABLE);
        request.setDepartmentId(deptId.toString());

        AppUser user = user(userId, AccountStatus.ACTIF, "EMPLOYE");
        when(userRepository.findByIdWithRoles(userId)).thenReturn(Optional.of(user));
        when(entityManager.find(Department.class, deptId)).thenReturn(null);

        assertThatThrownBy(() -> service.updateEmployeeRole(userId.toString(), request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void promote_codeCategorieVideOuBlanc_leveBadRequest() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UpdateRoleRequest request = new UpdateRoleRequest();
        request.setAction(UpdateRoleRequest.ActionEnum.PROMOTE_TO_RESPONSABLE);
        request.setDepartmentId(deptId.toString());
        request.setAssignedCategories(List.of("   "));

        AppUser user = user(userId, AccountStatus.ACTIF, "EMPLOYE");
        Department dept = new Department();
        dept.setActive(true);

        Role respRole = new Role();
        respRole.setCode("RESPONSABLE");

        when(userRepository.findByIdWithRoles(userId)).thenReturn(Optional.of(user));
        when(entityManager.find(Department.class, deptId)).thenReturn(dept);
        when(roleRepository.findById("RESPONSABLE")).thenReturn(Optional.of(respRole));

        assertThatThrownBy(() -> service.updateEmployeeRole(userId.toString(), request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Le code de catégorie est obligatoire.")
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void promote_categorieInexistanteOuDesactivee_leveBadRequest() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UpdateRoleRequest request = new UpdateRoleRequest();
        request.setAction(UpdateRoleRequest.ActionEnum.PROMOTE_TO_RESPONSABLE);
        request.setDepartmentId(deptId.toString());
        request.setAssignedCategories(List.of("INCONNUE"));

        AppUser user = user(userId, AccountStatus.ACTIF, "EMPLOYE");
        Department dept = new Department();
        dept.setActive(true);

        Role respRole = new Role();
        respRole.setCode("RESPONSABLE");

        when(userRepository.findByIdWithRoles(userId)).thenReturn(Optional.of(user));
        when(entityManager.find(Department.class, deptId)).thenReturn(dept);
        when(roleRepository.findById("RESPONSABLE")).thenReturn(Optional.of(respRole));
        when(ticketCategoryRepository.existsByCodeAndActiveTrue("INCONNUE")).thenReturn(false);

        assertThatThrownBy(() -> service.updateEmployeeRole(userId.toString(), request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Catégorie inexistante ou désactivée")
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void promote_nominal_creeProfilResponsableEtIncrementeSessionVersion() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        UpdateRoleRequest request = new UpdateRoleRequest();
        request.setAction(UpdateRoleRequest.ActionEnum.PROMOTE_TO_RESPONSABLE);
        request.setDepartmentId(deptId.toString());
        request.setIsConfidentialHab(true);
        request.setAssignedCategories(List.of("rh", "CONFLITS"));

        AppUser user = user(userId, AccountStatus.ACTIF, "EMPLOYE");
        user.setSessionVersion(0L);

        Department dept = new Department();
        dept.setActive(true);

        Role respRole = new Role();
        respRole.setCode("RESPONSABLE");

        EmployeeAccount expectedAccount = new EmployeeAccount();

        when(userRepository.findByIdWithRoles(userId)).thenReturn(Optional.of(user));
        when(entityManager.find(Department.class, deptId)).thenReturn(dept);
        when(roleRepository.findById("RESPONSABLE")).thenReturn(Optional.of(respRole));
        when(ticketCategoryRepository.existsByCodeAndActiveTrue("RH")).thenReturn(true);
        when(ticketCategoryRepository.existsByCodeAndActiveTrue("CONFLITS")).thenReturn(true);
        when(responsableRepository.findById(userId)).thenReturn(Optional.empty());
        when(responseMapper.toResponse(user)).thenReturn(expectedAccount);

        EmployeeAccount result = service.updateEmployeeRole(userId.toString(), request);

        assertThat(result).isSameAs(expectedAccount);
        assertThat(user.getSessionVersion()).isEqualTo(1L);
        assertThat(user.getRoles()).extracting(Role::getCode).contains("EMPLOYE", "RESPONSABLE");

        verify(responsableRepository).save(any(ResponsableAccount.class));
        verify(entityManager).flush();
    }

    @Test
    void revoke_nominal_retireRoleResponsableEtDesactiveProfil() {
        UUID userId = UUID.randomUUID();
        UpdateRoleRequest request = new UpdateRoleRequest();
        request.setAction(UpdateRoleRequest.ActionEnum.REVOKE_RESPONSABLE);

        AppUser user = user(userId, AccountStatus.ACTIF, "EMPLOYE", "RESPONSABLE");
        user.setSessionVersion(3L);

        ResponsableAccount account = new ResponsableAccount();
        account.setActive(true);
        account.setConfidentialHabilitation(true);
        account.getAssignedCategories().add("RH");

        EmployeeAccount expectedAccount = new EmployeeAccount();

        when(userRepository.findByIdWithRoles(userId)).thenReturn(Optional.of(user));
        when(responsableRepository.findById(userId)).thenReturn(Optional.of(account));
        when(responseMapper.toResponse(user)).thenReturn(expectedAccount);

        EmployeeAccount result = service.updateEmployeeRole(userId.toString(), request);

        assertThat(result).isSameAs(expectedAccount);
        assertThat(user.getSessionVersion()).isEqualTo(4L);
        assertThat(user.getRoles()).extracting(Role::getCode).containsExactly("EMPLOYE");
        assertThat(account.isActive()).isFalse();
        assertThat(account.isConfidentialHabilitation()).isFalse();
        assertThat(account.getAssignedCategories()).isEmpty();

        verify(entityManager).flush();
    }

    private static AppUser user(UUID id, AccountStatus status, String... roleCodes) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setStatus(status);
        user.setSessionVersion(0L);

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
