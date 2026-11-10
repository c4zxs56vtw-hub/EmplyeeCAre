package com.employeecare.api.statistics.service;

import com.employeecare.api.department.entity.Department;
import com.employeecare.api.generated.models.DashboardCount;
import com.employeecare.api.generated.models.DashboardSummary;
import com.employeecare.api.responsable.entity.ResponsableAccount;
import com.employeecare.api.responsable.repository.ResponsableAccountRepository;
import com.employeecare.api.statistics.repository.DashboardAggregationRepository;
import com.employeecare.api.ticket.entity.TicketStatus;
import com.employeecare.api.ticket.repository.TicketRepository;
import com.employeecare.api.user.entity.AccountStatus;
import com.employeecare.api.user.entity.AppUser;
import com.employeecare.api.user.entity.Role;
import com.employeecare.api.user.repository.AppUserRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private DashboardAggregationRepository aggregationRepository;

    @Mock
    private ResponsableAccountRepository responsableRepository;

    @Mock
    private AppUserRepository userRepository;

    @InjectMocks
    private StatisticsService statisticsService;

    @Test
    @DisplayName("getAdminDashboardStats - Calcule le total, la répartition par statut/catégorie et récupère les délais PostgreSQL")
    void getAdminDashboardStats_whenValidAdmin_shouldReturnSummary() {
        UUID adminId = UUID.randomUUID();
        Role adminRole = new Role();
        adminRole.setCode("ADMINISTRATEUR");

        AppUser admin = new AppUser();
        admin.setId(adminId);
        admin.setStatus(AccountStatus.ACTIF);
        admin.setRoles(Set.of(adminRole));

        when(userRepository.findByIdWithRoles(adminId)).thenReturn(Optional.of(admin));
        when(ticketRepository.countDashboardByStatusAdmin()).thenReturn(List.of(
                new Object[]{TicketStatus.EN_ATTENTE, 3L},
                new Object[]{TicketStatus.CLOTURE, 2L}
        ));
        when(ticketRepository.countDashboardByCategoryAdmin()).thenReturn(List.of(
                new Object[]{"TECHNIQUE", 4L},
                new Object[]{"ADMINISTRATIF", 1L}
        ));
        when(aggregationRepository.averageDurations(null, Set.of()))
                .thenReturn(new DashboardAggregationRepository.Delays(2.5, null));

        DashboardSummary summary = statisticsService.getAdminDashboardStats(adminId);

        assertThat(summary.getScope()).isEqualTo("ADMINISTRATEUR");
        assertThat(summary.getTotalTickets()).isEqualTo(5L);
        assertThat(summary.getAssignedToMe()).isNull();
        assertThat(summary.getByStatus()).hasSize(TicketStatus.values().length);
        assertThat(summary.getByCategory())
                .extracting(DashboardCount::getLabel)
                .containsExactly("ADMINISTRATIF", "TECHNIQUE");
        assertThat(summary.getAverageTakeChargeHours()).isEqualTo(2.5);
        assertThat(summary.getAverageClosureHours()).isNull();
    }

    @Test
    @DisplayName("getResponsableDashboardStats - Retourne les statistiques du périmètre responsable et assignedToMe")
    void getResponsableDashboardStats_whenValidResponsable_shouldReturnSummary() {
        UUID responsableId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Set<String> categories = Set.of("TECHNIQUE");

        Department department = new Department();
        department.setId(departmentId);

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
        responsable.setAssignedCategories(categories);

        when(responsableRepository.findById(responsableId)).thenReturn(Optional.of(responsable));
        when(ticketRepository.countDashboardByStatusResponsable(departmentId, categories)).thenReturn(List.<Object[]>of(
                new Object[]{TicketStatus.DIAGNOSTIC_EN_COURS, 4L}
        ));
        when(ticketRepository.countDashboardByCategoryResponsable(departmentId, categories)).thenReturn(List.<Object[]>of(
                new Object[]{"TECHNIQUE", 4L}
        ));
        when(aggregationRepository.averageDurations(departmentId, categories))
                .thenReturn(new DashboardAggregationRepository.Delays(1.75, 12.0));
        when(ticketRepository.countDashboardAssignedToResponsable(responsableId, departmentId, categories))
                .thenReturn(3L);

        DashboardSummary summary = statisticsService.getResponsableDashboardStats(responsableId);

        assertThat(summary.getScope()).isEqualTo("RESPONSABLE");
        assertThat(summary.getTotalTickets()).isEqualTo(4L);
        assertThat(summary.getAssignedToMe()).isEqualTo(3L);
        assertThat(summary.getAverageTakeChargeHours()).isEqualTo(1.75);
        assertThat(summary.getAverageClosureHours()).isEqualTo(12.0);
    }

    @Test
    @DisplayName("getAdminDashboardStats - Lève 403 si l'utilisateur n'a pas le rôle ADMINISTRATEUR")
    void getAdminDashboardStats_withoutAdminRole_shouldThrow403() {
        UUID userId = UUID.randomUUID();
        Role empRole = new Role();
        empRole.setCode("EMPLOYE");

        AppUser user = new AppUser();
        user.setId(userId);
        user.setStatus(AccountStatus.ACTIF);
        user.setRoles(Set.of(empRole));

        when(userRepository.findByIdWithRoles(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> statisticsService.getAdminDashboardStats(userId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
