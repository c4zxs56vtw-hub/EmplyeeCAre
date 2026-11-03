package com.employeecare.api.admin.service;

import com.employeecare.api.generated.models.CreateEmployeeRequest;
import com.employeecare.api.generated.models.EmployeeAccount;
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

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
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
}
