package com.employeecare.api.admin.service;

import com.employeecare.api.generated.models.CreateEmployeeRequest;
import com.employeecare.api.generated.models.EmployeeAccount;
import com.employeecare.api.user.entity.AppUser;
import com.employeecare.api.user.mapper.EmployeeAccountResponseMapper;
import com.employeecare.api.user.service.EmployeeService;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminEmployeeApplicationServiceTest {

    @Mock
    private EmployeeService employeeService;

    @Mock
    private EmployeeAccountResponseMapper responseMapper;

    @Mock
    private EntityManager entityManager;

    private AdminEmployeeApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AdminEmployeeApplicationService(
                employeeService,
                responseMapper,
                entityManager
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
}
