package com.employeecare.api.statistics.service;

import com.employeecare.api.department.entity.Department;
import com.employeecare.api.generated.models.DashboardAnalytics;
import com.employeecare.api.responsable.entity.ResponsableAccount;
import com.employeecare.api.responsable.repository.ResponsableAccountRepository;
import com.employeecare.api.user.entity.AccountStatus;
import com.employeecare.api.user.entity.AppUser;
import com.employeecare.api.user.entity.Role;
import com.employeecare.api.user.repository.AppUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardAnalyticsServiceTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private ResponsableAccountRepository responsableRepository;

    @InjectMocks
    private DashboardAnalyticsService analyticsService;

    @Test
    @DisplayName("getAdminAnalytics - Calcule les taux, la tendance mensuelle et la répartition par département")
    @SuppressWarnings("unchecked")
    void getAdminAnalytics_whenValidAdmin_shouldReturnAnalytics() {
        UUID adminId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        Role adminRole = new Role();
        adminRole.setCode("ADMINISTRATEUR");

        AppUser admin = new AppUser();
        admin.setId(adminId);
        admin.setStatus(AccountStatus.ACTIF);
        admin.setRoles(Set.of(adminRole));

        TypedQuery<Long> countQuery = mock(TypedQuery.class);
        when(countQuery.setParameter(anyString(), any())).thenReturn(countQuery);
        // 1er appel: ALL (80), 2e: RESOLVED (60), 3e: PROPOSED (40), 4e: REOPENED (8), puis 2 mois (3 et 25)
        when(countQuery.getSingleResult()).thenReturn(80L, 60L, 40L, 8L, 3L, 25L);
        when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);

        TypedQuery<Object[]> deptQuery = mock(TypedQuery.class);
        when(deptQuery.setParameter(anyString(), any())).thenReturn(deptQuery);
        when(deptQuery.getResultList()).thenReturn(List.<Object[]>of(new Object[]{deptId, 28L}));
        when(entityManager.createQuery(anyString(), eq(Object[].class))).thenReturn(deptQuery);

        when(userRepository.findByIdWithRoles(adminId)).thenReturn(Optional.of(admin));

        DashboardAnalytics result = analyticsService.getAdminAnalytics(adminId, 2);

        assertThat(result.getScope()).isEqualTo("ADMINISTRATEUR");
        assertThat(result.getMonths()).isEqualTo(2);
        assertThat(result.getResolutionRatePct()).isEqualTo(75.0);
        assertThat(result.getReopeningRatePct()).isEqualTo(20.0);
        assertThat(result.getMonthlyCreated()).hasSize(2);
        // 1er mois: count = 3 (< 5) -> suppressed = true, count = null
        assertThat(result.getMonthlyCreated().get(0).getSuppressed()).isTrue();
        assertThat(result.getMonthlyCreated().get(0).getCount()).isNull();
        // 2e mois: count = 25 (>= 5) -> suppressed = false, count = 25
        assertThat(result.getMonthlyCreated().get(1).getSuppressed()).isFalse();
        assertThat(result.getMonthlyCreated().get(1).getCount()).isEqualTo(25L);
        assertThat(result.getByDepartment()).hasSize(1);
        assertThat(result.getByDepartment().get(0).getDepartmentId()).isEqualTo(deptId);
        assertThat(result.getByDepartment().get(0).getCount()).isEqualTo(28L);
    }

    @Test
    @DisplayName("getAdminAnalytics - Lève 400 Bad Request si months > 12")
    void getAdminAnalytics_whenMonthsGreaterThan12_shouldThrow400() {
        UUID adminId = UUID.randomUUID();
        Role adminRole = new Role();
        adminRole.setCode("ADMINISTRATEUR");

        AppUser admin = new AppUser();
        admin.setId(adminId);
        admin.setStatus(AccountStatus.ACTIF);
        admin.setRoles(Set.of(adminRole));

        when(userRepository.findByIdWithRoles(adminId)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> analyticsService.getAdminAnalytics(adminId, 24))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("getResponsableAnalytics - Sans catégorie attribuée retourne des statistiques à zéro et byDepartment vide")
    void getResponsableAnalytics_withoutCategories_shouldReturnZeros() {
        UUID responsableId = UUID.randomUUID();
        Department department = new Department();
        department.setId(UUID.randomUUID());

        Role respRole = new Role();
        respRole.setCode("RESPONSABLE");

        AppUser user = new AppUser();
        user.setId(responsableId);
        user.setStatus(AccountStatus.ACTIF);
        user.setRoles(Set.of(respRole));

        ResponsableAccount responsable = new ResponsableAccount();
        responsable.setUserId(responsableId);
        responsable.setUser(user);
        responsable.setActive(true);
        responsable.setDepartment(department);
        responsable.setAssignedCategories(Set.of());

        when(responsableRepository.findById(responsableId)).thenReturn(Optional.of(responsable));

        DashboardAnalytics result = analyticsService.getResponsableAnalytics(responsableId, 3);

        assertThat(result.getScope()).isEqualTo("RESPONSABLE");
        assertThat(result.getMonths()).isEqualTo(3);
        assertThat(result.getMonthlyCreated()).hasSize(3);
        assertThat(result.getResolutionRatePct()).isNull();
        assertThat(result.getReopeningRatePct()).isNull();
        assertThat(result.getByDepartment()).isEmpty();
    }
}
