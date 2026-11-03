package com.employeecare.api.responsable.entity;

import com.employeecare.api.department.entity.Department;
import com.employeecare.api.user.entity.AppUser;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ResponsableAccountTest {

    @Test
    void defaultValues_sontConformes() {
        ResponsableAccount account = new ResponsableAccount();

        assertThat(account.isActive()).isTrue();
        assertThat(account.isConfidentialHabilitation()).isFalse();
        assertThat(account.getAssignedCategories()).isNotNull().isEmpty();
    }

    @Test
    void gettersAndSetters_fonctionnentCorrectement() {
        ResponsableAccount account = new ResponsableAccount();
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser();
        Department department = new Department();
        Instant now = Instant.now();

        account.setUserId(userId);
        account.setUser(user);
        account.setDepartment(department);
        account.setActive(false);
        account.setConfidentialHabilitation(true);
        account.setAssignedCategories(Set.of("CAT_RH", "CAT_TECH"));
        account.setCreatedAt(now);
        account.setUpdatedAt(now);

        assertThat(account.getUserId()).isEqualTo(userId);
        assertThat(account.getUser()).isSameAs(user);
        assertThat(account.getDepartment()).isSameAs(department);
        assertThat(account.isActive()).isFalse();
        assertThat(account.isConfidentialHabilitation()).isTrue();
        assertThat(account.getAssignedCategories()).containsExactlyInAnyOrder("CAT_RH", "CAT_TECH");
        assertThat(account.getCreatedAt()).isEqualTo(now);
        assertThat(account.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void assignedCategories_permetAjoutEtSuppression() {
        ResponsableAccount account = new ResponsableAccount();

        account.getAssignedCategories().add("SANTE_SECURITE");
        account.getAssignedCategories().add("CONDITIONS_TRAVAIL");

        assertThat(account.getAssignedCategories())
                .hasSize(2)
                .contains("SANTE_SECURITE", "CONDITIONS_TRAVAIL");

        account.getAssignedCategories().remove("SANTE_SECURITE");

        assertThat(account.getAssignedCategories())
                .hasSize(1)
                .containsExactly("CONDITIONS_TRAVAIL");
    }
}
