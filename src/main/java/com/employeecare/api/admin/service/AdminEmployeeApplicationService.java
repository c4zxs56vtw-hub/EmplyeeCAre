package com.employeecare.api.admin.service;

import com.employeecare.api.generated.models.CreateEmployeeRequest;
import com.employeecare.api.generated.models.EmployeeAccount;

import com.employeecare.api.user.entity.AppUser;
import com.employeecare.api.user.mapper.EmployeeAccountResponseMapper;
import com.employeecare.api.user.service.EmployeeService;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminEmployeeApplicationService {

    private final EmployeeService employeeService;

    private final EmployeeAccountResponseMapper responseMapper;

    private final EntityManager entityManager;

    public AdminEmployeeApplicationService(
            EmployeeService employeeService,
            EmployeeAccountResponseMapper responseMapper,
            EntityManager entityManager
    ) {
        this.employeeService = employeeService;
        this.responseMapper = responseMapper;
        this.entityManager = entityManager;
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
}
