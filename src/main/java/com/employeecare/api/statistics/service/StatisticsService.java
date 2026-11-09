package com.employeecare.api.statistics.service;

import com.employeecare.api.generated.models.DashboardCount;
import com.employeecare.api.generated.models.DashboardSummary;

import com.employeecare.api.responsable.entity.ResponsableAccount;
import com.employeecare.api.responsable.repository.ResponsableAccountRepository;

import com.employeecare.api.ticket.entity.TicketStatus;
import com.employeecare.api.ticket.repository.TicketEventRepository;
import com.employeecare.api.ticket.repository.TicketRepository;

import com.employeecare.api.user.entity.AccountStatus;
import com.employeecare.api.user.entity.AppUser;
import com.employeecare.api.user.repository.AppUserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class StatisticsService {

    private final TicketRepository ticketRepository;
    private final TicketEventRepository eventRepository;
    private final ResponsableAccountRepository responsableRepository;
    private final AppUserRepository userRepository;

    public StatisticsService(

            TicketRepository ticketRepository,

            TicketEventRepository eventRepository,

            ResponsableAccountRepository responsableRepository,

            AppUserRepository userRepository

    ) {

        this.ticketRepository = ticketRepository;

        this.eventRepository = eventRepository;

        this.responsableRepository = responsableRepository;

        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummary getAdminDashboardStats(
            UUID adminId
    ) {

        // ==========================================
        // 1. VERIFIER L'ADMINISTRATEUR
        // ==========================================

        AppUser admin = userRepository
                .findByIdWithRoles(adminId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Accès non autorisé."
                ));

        if (admin.getStatus() != AccountStatus.ACTIF) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Compte désactivé."
            );
        }

        boolean isAdmin = admin.getRoles()
                .stream()
                .anyMatch(role ->
                        "ADMINISTRATEUR".equals(role.getCode())
                );

        if (!isAdmin) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Rôle administrateur requis."
            );
        }


        // ==========================================
        // 2. RECUPERER LES STATISTIQUES
        // ==========================================

        List<Object[]> statusRows =
                ticketRepository.countDashboardByStatusAdmin();

        List<Object[]> categoryRows =
                ticketRepository.countDashboardByCategoryAdmin();


        // ==========================================
        // 3. RECUPERER LES DATES
        // ==========================================

        List<Object[]> takeChargeRows =
                eventRepository.findDashboardDurationsAdmin(
                        "PRISE_EN_CHARGE"
                );

        List<Object[]> closureRows =
                eventRepository.findDashboardDurationsAdmin(
                        "CLOTURE_SIGNALEMENT"
                );


        // ==========================================
        // 4. CONSTRUIRE LA REPONSE
        // ==========================================

        return buildSummary(
                "ADMINISTRATEUR",
                statusRows,
                categoryRows,
                takeChargeRows,
                closureRows,
                null
        );
    }

    @Transactional(readOnly = true)
    public DashboardSummary getResponsableDashboardStats(
            UUID responsableId
    ) {

        // ==========================================
        // 1. RECUPERER LE PROFIL RESPONSABLE
        // ==========================================

        ResponsableAccount responsable =
                responsableRepository
                        .findById(responsableId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "Profil responsable introuvable."
                        ));


        // ==========================================
        // 2. VERIFIER LE PROFIL
        // ==========================================

        if (!responsable.isActive()) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Profil responsable désactivé."
            );
        }


        // ==========================================
        // 3. VERIFIER LE COMPTE UTILISATEUR
        // ==========================================

        AppUser user = responsable.getUser();

        if (user.getStatus() != AccountStatus.ACTIF) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Compte utilisateur désactivé."
            );
        }

        boolean hasRole = user.getRoles()
                .stream()
                .anyMatch(role ->
                        "RESPONSABLE".equals(role.getCode())
                );

        if (!hasRole) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Rôle responsable requis."
            );
        }


        // ==========================================
        // 4. VERIFIER LE DEPARTEMENT
        // ==========================================

        if (responsable.getDepartment() == null) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Aucun département attribué."
            );
        }

        UUID departmentId =
                responsable.getDepartment().getId();


        // ==========================================
        // 5. RECUPERER LES CATEGORIES AUTORISEES
        // ==========================================

        Set<String> categoryCodes =
                responsable.getAssignedCategories();


        // ==========================================
        // 6. GERER LES RESPONSABLES SANS CATEGORIE
        // ==========================================

        if (categoryCodes == null || categoryCodes.isEmpty()) {

            return buildSummary(
                    "RESPONSABLE",
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    0L
            );
        }


        // ==========================================
        // 7. RECUPERER LES STATISTIQUES
        // ==========================================

        List<Object[]> statusRows =
                ticketRepository.countDashboardByStatusResponsable(
                        departmentId,
                        categoryCodes
                );

        List<Object[]> categoryRows =
                ticketRepository.countDashboardByCategoryResponsable(
                        departmentId,
                        categoryCodes
                );


        // ==========================================
        // 8. RECUPERER LES DELAIS
        // ==========================================

        List<Object[]> takeChargeRows =
                eventRepository.findDashboardDurationsResponsable(
                        "PRISE_EN_CHARGE",
                        departmentId,
                        categoryCodes
                );

        List<Object[]> closureRows =
                eventRepository.findDashboardDurationsResponsable(
                        "CLOTURE_SIGNALEMENT",
                        departmentId,
                        categoryCodes
                );


        // ==========================================
        // 9. COMPTER SES DOSSIERS ATTRIBUES
        // ==========================================

        long assignedToMe =
                ticketRepository.countDashboardAssignedToResponsable(
                        responsableId,
                        departmentId,
                        categoryCodes
                );


        // ==========================================
        // 10. CONSTRUIRE LA REPONSE
        // ==========================================

        return buildSummary(
                "RESPONSABLE",
                statusRows,
                categoryRows,
                takeChargeRows,
                closureRows,
                assignedToMe
        );
    }

    private DashboardSummary buildSummary(

            String scope,

            List<Object[]> statusRows,

            List<Object[]> categoryRows,

            List<Object[]> takeChargeRows,

            List<Object[]> closureRows,

            Long assignedToMe

    ) {

        DashboardSummary response = new DashboardSummary();


        // ==========================================
        // 1. PERIMETRE
        // ==========================================

        response.setScope(scope);


        // ==========================================
        // 2. REPARTITION PAR STATUT
        // ==========================================

        Map<TicketStatus, Long> statusCounts =
                new HashMap<>();

        for (Object[] row : statusRows) {

            TicketStatus status = (TicketStatus) row[0];

            long count = ((Number) row[1]).longValue();

            statusCounts.put(status, count);
        }

        List<DashboardCount> byStatus =
                new ArrayList<>();

        for (TicketStatus status : TicketStatus.values()) {

            DashboardCount item = new DashboardCount();

            item.setLabel(status.name());

            item.setCount(
                    statusCounts.getOrDefault(status, 0L)
            );

            byStatus.add(item);
        }

        response.setByStatus(byStatus);


        // ==========================================
        // 3. NOMBRE TOTAL
        // ==========================================

        long total = statusCounts.values()
                .stream()
                .mapToLong(Long::longValue)
                .sum();

        response.setTotalTickets(total);


        // ==========================================
        // 4. REPARTITION PAR CATEGORIE
        // ==========================================

        List<DashboardCount> byCategory =
                new ArrayList<>();

        for (Object[] row : categoryRows) {

            DashboardCount item = new DashboardCount();

            item.setLabel(
                    (String) row[0]
            );

            item.setCount(
                    ((Number) row[1]).longValue()
            );

            byCategory.add(item);
        }

        byCategory.sort(
                Comparator.comparing(DashboardCount::getLabel)
        );

        response.setByCategory(byCategory);


        // ==========================================
        // 5. SIGNALEMENTS ATTRIBUES
        // ==========================================

        if (assignedToMe != null) {

            response.setAssignedToMe(assignedToMe);
        }


        // ==========================================
        // 6. DELAIS MOYENS
        // ==========================================

        response.setAverageTakeChargeHours(
                calculateAverageHours(takeChargeRows)
        );

        response.setAverageClosureHours(
                calculateAverageHours(closureRows)
        );


        // ==========================================
        // 7. DATE DE GENERATION
        // ==========================================

        response.setGeneratedAt(
                OffsetDateTime.now(ZoneOffset.UTC)
        );


        return response;
    }

    private Double calculateAverageHours(
            List<Object[]> rows
    ) {

        if (rows.isEmpty()) {
            return null;
        }

        double totalHours = 0;

        int validRecords = 0;

        for (Object[] row : rows) {

            Instant start = (Instant) row[0];

            Instant end = (Instant) row[1];

            if (start == null || end == null) {
                continue;
            }

            if (end.isBefore(start)) {
                continue;
            }

            double hours = Duration
                    .between(start, end)
                    .toMillis() / 3_600_000.0;

            totalHours += hours;

            validRecords++;
        }

        if (validRecords == 0) {
            return null;
        }

        double average = totalHours / validRecords;

        return Math.round(average * 100.0) / 100.0;
    }
}
