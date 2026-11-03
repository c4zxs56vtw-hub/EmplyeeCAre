package com.employeecare.api.admin.service;

import com.employeecare.api.generated.models.CreateEmployeeRequest;
import com.employeecare.api.generated.models.EmployeeAccount;

import com.employeecare.api.user.entity.AppUser;
import com.employeecare.api.user.mapper.EmployeeAccountResponseMapper;
import com.employeecare.api.user.repository.AppUserRepository;
import com.employeecare.api.user.service.EmployeeService;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
}
