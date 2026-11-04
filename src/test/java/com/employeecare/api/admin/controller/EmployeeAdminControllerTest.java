package com.employeecare.api.admin.controller;

import com.employeecare.api.admin.service.AdminEmployeeApplicationService;
import com.employeecare.api.responsable.service.ResponsableRoleService;
import com.employeecare.api.generated.models.CreateEmployeeRequest;
import com.employeecare.api.generated.models.EmployeeAccount;
import com.employeecare.api.generated.models.UpdateEmployeeStatusRequest;
import com.employeecare.api.generated.models.UpdateRoleRequest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeAdminControllerTest {

    @Mock
    private AdminEmployeeApplicationService adminEmployeeService;

    @Mock
    private ResponsableRoleService responsableRoleService;

    @InjectMocks
    private EmployeeAdminController controller;

    @Test
    void listEmployees_retourne200AvecLaListeDesEmployes() {

        List<EmployeeAccount> expected = List.of(new EmployeeAccount());
        when(adminEmployeeService.listEmployees()).thenReturn(expected);

        ResponseEntity<List<EmployeeAccount>> response =
                controller.listEmployees();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(adminEmployeeService).listEmployees();
    }

    @Test
    void createEmployeeAccount_retourne201AvecLeCompteCree() {

        CreateEmployeeRequest request = new CreateEmployeeRequest();
        EmployeeAccount created = new EmployeeAccount();

        when(adminEmployeeService.createEmployee(request)).thenReturn(created);

        ResponseEntity<EmployeeAccount> response =
                controller.createEmployeeAccount(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(created);
        verify(adminEmployeeService).createEmployee(request);
    }

    @Test
    void createEmployeeAccount_propageLesErreursMetier() {

        CreateEmployeeRequest request = new CreateEmployeeRequest();

        when(adminEmployeeService.createEmployee(request))
                .thenThrow(new IllegalArgumentException("Cette adresse email est déjà utilisée."));

        assertThatThrownBy(() -> controller.createEmployeeAccount(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("déjà utilisée");
    }

    @Test
    void updateEmployeeStatus_retourne200AvecLeCompteMisAJour() {

        String employeeId = UUID.randomUUID().toString();
        UpdateEmployeeStatusRequest request = new UpdateEmployeeStatusRequest();
        EmployeeAccount expected = new EmployeeAccount();

        when(adminEmployeeService.updateEmployeeStatus(employeeId, request)).thenReturn(expected);

        ResponseEntity<EmployeeAccount> response =
                controller.updateEmployeeStatus(employeeId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(adminEmployeeService).updateEmployeeStatus(employeeId, request);
    }

    @Test
    void updateEmployeeRole_retourne200AvecLeCompteMisAJour() {

        String employeeId = UUID.randomUUID().toString();
        UpdateRoleRequest request = new UpdateRoleRequest();
        EmployeeAccount expected = new EmployeeAccount();

        when(responsableRoleService.updateEmployeeRole(employeeId, request)).thenReturn(expected);

        ResponseEntity<EmployeeAccount> response =
                controller.updateEmployeeRole(employeeId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(responsableRoleService).updateEmployeeRole(employeeId, request);
    }
}
