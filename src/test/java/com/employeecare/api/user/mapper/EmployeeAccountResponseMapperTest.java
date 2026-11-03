package com.employeecare.api.user.mapper;

import com.employeecare.api.department.entity.Department;
import com.employeecare.api.generated.models.AccountStatus;
import com.employeecare.api.generated.models.EmployeeAccount;
import com.employeecare.api.generated.models.UserRole;
import com.employeecare.api.user.entity.AppUser;
import com.employeecare.api.user.entity.Role;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EmployeeAccountResponseMapperTest {

    private final EmployeeAccountResponseMapper mapper = new EmployeeAccountResponseMapper();

    @Test
    void toResponse_employe_mappeTousLesChamps() {

        UUID userId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();

        AppUser user = user(userId, departmentId, "EMPLOYE");
        user.setCreatedAt(Instant.parse("2026-11-02T10:00:00Z"));

        EmployeeAccount response = mapper.toResponse(user);

        assertThat(response.getId()).isEqualTo(userId.toString());
        assertThat(response.getMatricule()).isEqualTo("EC-2026-001");
        assertThat(response.getFirstName()).isEqualTo("Claire");
        assertThat(response.getLastName()).isEqualTo("Moreau");
        assertThat(response.getFullName()).isEqualTo("Claire Moreau");
        assertThat(response.getEmail()).isEqualTo("claire.moreau@entreprise.com");
        assertThat(response.getPhone()).isEqualTo("+237690515143");
        assertThat(response.getPosition()).isEqualTo("Chargée RH");
        assertThat(response.getDepartmentId()).isEqualTo(departmentId.toString());
        assertThat(response.getDepartmentName()).isEqualTo("Administration");
        assertThat(response.getRoles()).containsExactly(UserRole.EMPLOYE);
        assertThat(response.getPrimaryRoleLabel())
                .isEqualTo(EmployeeAccount.PrimaryRoleLabelEnum.fromValue("Employé"));
        assertThat(response.getStatus()).isEqualTo(AccountStatus.EN_ATTENTE_ACTIVATION);
        assertThat(response.getCreatedAt()).isEqualTo("02/11/2026");
    }

    @Test
    void toResponse_responsable_libelleResponsableEtRolesTries() {

        AppUser user = user(UUID.randomUUID(), UUID.randomUUID(), "RESPONSABLE", "EMPLOYE");
        user.setCreatedAt(Instant.parse("2026-11-02T10:00:00Z"));

        EmployeeAccount response = mapper.toResponse(user);

        assertThat(response.getRoles())
                .containsExactly(UserRole.EMPLOYE, UserRole.RESPONSABLE);
        assertThat(response.getPrimaryRoleLabel())
                .isEqualTo(EmployeeAccount.PrimaryRoleLabelEnum.fromValue("Responsable"));
    }

    @Test
    void toResponse_dateFormateeDansLeFuseauDeDouala() {

        AppUser user = user(UUID.randomUUID(), UUID.randomUUID(), "EMPLOYE");

        // 23h30 UTC le 1er novembre = 00h30 le 2 novembre à Douala (UTC+1)
        user.setCreatedAt(Instant.parse("2026-11-01T23:30:00Z"));

        assertThat(mapper.toResponse(user).getCreatedAt()).isEqualTo("02/11/2026");
    }

    private static AppUser user(UUID userId, UUID departmentId, String... roleCodes) {

        Department department = new Department();
        department.setId(departmentId);
        department.setName("Administration");

        AppUser user = new AppUser();
        user.setId(userId);
        user.setEmployeeNumber("EC-2026-001");
        user.setFirstName("Claire");
        user.setLastName("Moreau");
        user.setEmail("claire.moreau@entreprise.com");
        user.setPhone("+237690515143");
        user.setJobTitle("Chargée RH");
        user.setDepartment(department);
        user.setStatus(com.employeecare.api.user.entity.AccountStatus.EN_ATTENTE_ACTIVATION);

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
