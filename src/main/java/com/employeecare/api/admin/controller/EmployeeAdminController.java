package com.employeecare.api.admin.controller;

import com.employeecare.api.admin.service.AdminEmployeeApplicationService;

import com.employeecare.api.generated.apis.EspaceAdministrateurApi;

import com.employeecare.api.generated.models.CreateEmployeeRequest;
import com.employeecare.api.generated.models.EmployeeAccount;
import com.employeecare.api.generated.models.UpdateEmployeeStatusRequest;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class EmployeeAdminController implements EspaceAdministrateurApi {

    private final AdminEmployeeApplicationService adminEmployeeService;

    public EmployeeAdminController(
            AdminEmployeeApplicationService adminEmployeeService
    ) {
        this.adminEmployeeService = adminEmployeeService;
    }

    @Override
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public ResponseEntity<List<EmployeeAccount>> listEmployees() {

        List<EmployeeAccount> employees =
                adminEmployeeService.listEmployees();

        return ResponseEntity.ok(employees);
    }

    @Override
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public ResponseEntity<EmployeeAccount> createEmployeeAccount(
            @Valid @RequestBody CreateEmployeeRequest request
    ) {

        EmployeeAccount employee =
                adminEmployeeService.createEmployee(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(employee);
    }

    @Override
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public ResponseEntity<EmployeeAccount> updateEmployeeStatus(
            @PathVariable("employeeId") String employeeId,
            @Valid @RequestBody UpdateEmployeeStatusRequest request
    ) {

        EmployeeAccount employee =
                adminEmployeeService.updateEmployeeStatus(
                        employeeId,
                        request
                );

        return ResponseEntity.ok(employee);
    }
}
