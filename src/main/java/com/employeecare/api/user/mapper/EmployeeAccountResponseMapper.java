package com.employeecare.api.user.mapper;


import com.employeecare.api.generated.models.EmployeeAccount;
import com.employeecare.api.generated.models.UserRole;
import com.employeecare.api.user.entity.AppUser;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@Component

public class EmployeeAccountResponseMapper {
    private static DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE
            .ofPattern("dd/MM/uuuu")
            .withZone(ZoneId.of("Africa/Douala"));

    public EmployeeAccount toResponse(AppUser user){
        EmployeeAccount response = new EmployeeAccount();


        response.setId(user.getId().toString());

        response.setMatricule(user.getEmployeeNumber());

        response.setFirstName(user.getFirstName());

        response.setLastName(user.getLastName());

        response.setFullName(
                user.getFirstName() + " " + user.getLastName()
        );

        response.setEmail(user.getEmail());

        response.setPhone(user.getPhone());

        response.setPosition(user.getJobTitle());

        response.setDepartmentId(
                user.getDepartment().getId().toString()
        );

        response.setDepartmentName(
                user.getDepartment().getName()
        );

        List<UserRole> roles = user.getRoles()
                .stream()
                .map(role -> UserRole.fromValue(role.getCode()))
                .sorted(Comparator.comparing(UserRole::name))
                .toList();

        response.setRoles(roles);

        boolean isResponsable = roles.contains(UserRole.RESPONSABLE);

        String roleLabel = isResponsable
                ? "Responsable"
                : "Employé";

        response.setPrimaryRoleLabel(
                EmployeeAccount.PrimaryRoleLabelEnum
                        .fromValue(roleLabel)
        );

        response.setStatus(
                com.employeecare.api.generated.models.AccountStatus
                        .fromValue(user.getStatus().name())
        );

        response.setCreatedAt(
                DATE_FORMAT.format(user.getCreatedAt())
        );

        return response;
    }
    }

