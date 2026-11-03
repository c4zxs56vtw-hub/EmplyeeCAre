package com.employeecare.api.admin.service;

import com.employeecare.api.generated.models.AccountStatus;
import com.employeecare.api.generated.models.CreateEmployeeRequest;
import com.employeecare.api.generated.models.EmployeeAccount;
import com.employeecare.api.generated.models.UpdateEmployeeStatusRequest;
import com.employeecare.api.user.entity.AppUser;
import com.employeecare.api.user.entity.Role;
import com.employeecare.api.user.mapper.EmployeeAccountResponseMapper;
import com.employeecare.api.user.repository.AppUserRepository;
import com.employeecare.api.user.service.EmployeeService;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminEmployeeApplicationServiceTest {

    @Mock
    private EmployeeService employeeService;

    @Mock
    private EmployeeAccountResponseMapper responseMapper;

    @Mock
    private EntityManager entityManager;

    @Mock
    private AppUserRepository appUserRepository;

    private AdminEmployeeApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AdminEmployeeApplicationService(
                employeeService,
                responseMapper,
                entityManager,
                appUserRepository
        );
    }

    @Test
    void createEmployee_creeEmployeFlushEtMappeLaReponseDansLOrdre() {

        CreateEmployeeRequest request = new CreateEmployeeRequest();
        AppUser createdUser = new AppUser();
        EmployeeAccount expectedResponse = new EmployeeAccount();

        when(employeeService.createEmployee(request)).thenReturn(createdUser);
        when(responseMapper.toResponse(createdUser)).thenReturn(expectedResponse);

        EmployeeAccount actual = service.createEmployee(request);

        assertThat(actual).isSameAs(expectedResponse);

        InOrder inOrder = inOrder(employeeService, entityManager, responseMapper);
        inOrder.verify(employeeService).createEmployee(request);
        inOrder.verify(entityManager).flush();
        inOrder.verify(responseMapper).toResponse(createdUser);
    }

    @Test
    void listEmployees_filtreEmployesEtMappeReponse() {

        Role employeRole = new Role();
        employeRole.setCode("EMPLOYE");

        Role adminRole = new Role();
        adminRole.setCode("ADMINISTRATEUR");

        AppUser user1 = new AppUser();
        user1.setRoles(Set.of(employeRole));

        AppUser user2 = new AppUser();
        user2.setRoles(Set.of(adminRole));

        when(appUserRepository.findAllByOrderByRolesDesc()).thenReturn(List.of(user1, user2));
        EmployeeAccount expectedAccount = new EmployeeAccount();
        when(responseMapper.toResponse(user1)).thenReturn(expectedAccount);

        List<EmployeeAccount> result = service.listEmployees();

        assertThat(result).containsExactly(expectedAccount);
        verify(appUserRepository).findAllByOrderByRolesDesc();
        verify(responseMapper).toResponse(user1);
    }

    @Test
    void updateEmployeeStatus_requeteNulle_leveBadRequest() {

        assertThatThrownBy(() -> service.updateEmployeeStatus(UUID.randomUUID().toString(), null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void updateEmployeeStatus_idInvalide_leveBadRequest() {

        UpdateEmployeeStatusRequest request = new UpdateEmployeeStatusRequest();
        request.setStatus(AccountStatus.DESACTIVE);

        assertThatThrownBy(() -> service.updateEmployeeStatus("id-invalide", request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void updateEmployeeStatus_utilisateurIntrouvable_leveNotFound() {

        UUID userId = UUID.randomUUID();
        UpdateEmployeeStatusRequest request = new UpdateEmployeeStatusRequest();
        request.setStatus(AccountStatus.DESACTIVE);

        when(appUserRepository.findByIdWithRoles(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateEmployeeStatus(userId.toString(), request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateEmployeeStatus_compteAdministrateur_leveForbidden() {

        UUID userId = UUID.randomUUID();
        UpdateEmployeeStatusRequest request = new UpdateEmployeeStatusRequest();
        request.setStatus(AccountStatus.DESACTIVE);

        Role adminRole = new Role();
        adminRole.setCode("ADMINISTRATEUR");

        AppUser admin = new AppUser();
        admin.setRoles(Set.of(adminRole));

        when(appUserRepository.findByIdWithRoles(userId)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.updateEmployeeStatus(userId.toString(), request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void updateEmployeeStatus_compteEnAttenteActivation_leveConflict() {

        UUID userId = UUID.randomUUID();
        UpdateEmployeeStatusRequest request = new UpdateEmployeeStatusRequest();
        request.setStatus(AccountStatus.ACTIF);

        Role employeRole = new Role();
        employeRole.setCode("EMPLOYE");

        AppUser user = new AppUser();
        user.setStatus(com.employeecare.api.user.entity.AccountStatus.EN_ATTENTE_ACTIVATION);
        user.setRoles(Set.of(employeRole));

        when(appUserRepository.findByIdWithRoles(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.updateEmployeeStatus(userId.toString(), request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void updateEmployeeStatus_nouveauStatutEnAttenteActivation_leveBadRequest() {

        UUID userId = UUID.randomUUID();
        UpdateEmployeeStatusRequest request = new UpdateEmployeeStatusRequest();
        request.setStatus(AccountStatus.EN_ATTENTE_ACTIVATION);

        Role employeRole = new Role();
        employeRole.setCode("EMPLOYE");

        AppUser user = new AppUser();
        user.setStatus(com.employeecare.api.user.entity.AccountStatus.ACTIF);
        user.setRoles(Set.of(employeRole));

        when(appUserRepository.findByIdWithRoles(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.updateEmployeeStatus(userId.toString(), request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void updateEmployeeStatus_changementStatut_incrementeSessionVersionEtMappeReponse() {

        UUID userId = UUID.randomUUID();
        UpdateEmployeeStatusRequest request = new UpdateEmployeeStatusRequest();
        request.setStatus(AccountStatus.DESACTIVE);

        Role employeRole = new Role();
        employeRole.setCode("EMPLOYE");

        AppUser user = new AppUser();
        user.setStatus(com.employeecare.api.user.entity.AccountStatus.ACTIF);
        user.setSessionVersion(0L);
        user.setRoles(Set.of(employeRole));

        EmployeeAccount expectedAccount = new EmployeeAccount();

        when(appUserRepository.findByIdWithRoles(userId)).thenReturn(Optional.of(user));
        when(responseMapper.toResponse(user)).thenReturn(expectedAccount);

        EmployeeAccount result = service.updateEmployeeStatus(userId.toString(), request);

        assertThat(result).isSameAs(expectedAccount);
        assertThat(user.getStatus()).isEqualTo(com.employeecare.api.user.entity.AccountStatus.DESACTIVE);
        assertThat(user.getSessionVersion()).isEqualTo(1L);
    }
}
