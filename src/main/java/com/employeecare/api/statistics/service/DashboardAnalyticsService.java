package com.employeecare.api.statistics.service;

import com.employeecare.api.generated.models.DashboardAnalytics;
import com.employeecare.api.generated.models.DepartmentTicketMetric;
import com.employeecare.api.generated.models.MonthlyTicketMetric;

import com.employeecare.api.responsable.entity.ResponsableAccount;
import com.employeecare.api.responsable.repository.ResponsableAccountRepository;

import com.employeecare.api.statistics.repository.DashboardAggregationRepository;

import com.employeecare.api.ticket.entity.TicketStatus;

import com.employeecare.api.user.entity.AccountStatus;
import com.employeecare.api.user.entity.AppUser;
import com.employeecare.api.user.repository.AppUserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DashboardAnalyticsService {

    private static final ZoneId ZONE =
            ZoneId.of("Africa/Douala");

    private static final long MIN_GROUP_SIZE = 5;

    private final EntityManager entityManager;
    private final DashboardAggregationRepository aggregationRepository;
    private final AppUserRepository userRepository;
    private final ResponsableAccountRepository responsableRepository;

    public DashboardAnalyticsService(

            EntityManager entityManager,

            DashboardAggregationRepository aggregationRepository,

            AppUserRepository userRepository,

            ResponsableAccountRepository responsableRepository

    ) {

        this.entityManager = entityManager;

        this.aggregationRepository = aggregationRepository;

        this.userRepository = userRepository;

        this.responsableRepository = responsableRepository;
    }

    private record Scope(
            UUID departmentId,
            Set<String> categoryCodes
    ) {
        boolean isResponsable() {
            return departmentId != null;
        }
    }

    private enum CountKind {
        ALL,
        RESOLVED,
        PROPOSED,
        REOPENED
    }

    @Transactional(readOnly = true)
    public DashboardAnalytics getAdminAnalytics(
            UUID adminId,
            Integer months
    ) {

        AppUser admin = userRepository
                .findByIdWithRoles(adminId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Administrateur introuvable."
                ));

        boolean hasAdminRole = admin.getRoles()
                .stream()
                .anyMatch(role ->
                        "ADMINISTRATEUR".equals(role.getCode())
                );

        if (admin.getStatus() != AccountStatus.ACTIF
                || !hasAdminRole) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Accès administratif interdit."
            );
        }

        return buildAnalytics(
                "ADMINISTRATEUR",
                new Scope(null, Set.of()),
                validateMonths(months)
        );
    }

    @Transactional(readOnly = true)
    public DashboardAnalytics getResponsableAnalytics(
            UUID responsableId,
            Integer months
    ) {

        ResponsableAccount responsable =
                responsableRepository
                        .findById(responsableId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "Responsable introuvable."
                        ));

        if (!responsable.isActive()
                || responsable.getDepartment() == null) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Profil responsable non autorisé."
            );
        }

        AppUser user = responsable.getUser();

        boolean hasRole = user.getRoles()
                .stream()
                .anyMatch(role ->
                        "RESPONSABLE".equals(role.getCode())
                );

        if (user.getStatus() != AccountStatus.ACTIF
                || !hasRole) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Compte responsable non autorisé."
            );
        }

        Set<String> categories =
                responsable.getAssignedCategories() == null
                        ? Set.of()
                        : Set.copyOf(
                                responsable.getAssignedCategories()
                        );

        Scope scope = new Scope(
                responsable.getDepartment().getId(),
                categories
        );

        return buildAnalytics(
                "RESPONSABLE",
                scope,
                validateMonths(months)
        );
    }

    private int validateMonths(Integer months) {

        int value = months == null ? 6 : months;

        if (value < 1 || value > 12) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La période doit être comprise entre 1 et 12 mois."
            );
        }

        return value;
    }

    private long countTickets(
            Scope scope,
            Instant start,
            Instant end,
            CountKind kind
    ) {

        // Un responsable sans catégorie autorisée
        // ne peut obtenir aucune statistique.
        if (scope.isResponsable()
                && scope.categoryCodes().isEmpty()) {

            return 0L;
        }

        // Filtres communs.
        String jpql = """
                SELECT COUNT(t)
                FROM Ticket t
                WHERE t.anonymous = false
                  AND t.confidential = false
                  AND t.createdAt >= :start
                  AND t.createdAt < :end
                """;

        // Filtres supplémentaires du responsable.
        if (scope.isResponsable()) {

            jpql += """
                      AND t.department.id = :departmentId
                      AND t.category.code IN :categoryCodes
                    """;
        }

        // Sélection du type de calcul.
        switch (kind) {

            case RESOLVED -> jpql += """
                      AND t.status IN :resolvedStatuses
                    """;

            case PROPOSED -> jpql += """
                      AND EXISTS (
                          SELECT e.id
                          FROM TicketEvent e
                          WHERE e.ticket = t
                            AND e.eventType = 'SOUMISSION_VALIDATION'
                      )
                    """;

            case REOPENED -> jpql += """
                      AND EXISTS (
                          SELECT e.id
                          FROM TicketEvent e
                          WHERE e.ticket = t
                            AND e.eventType = 'SOUMISSION_VALIDATION'
                      )
                      AND EXISTS (
                          SELECT r.id
                          FROM TicketEvent r
                          WHERE r.ticket = t
                            AND r.eventType = 'SOLUTION_REFUSEE'
                      )
                    """;

            case ALL -> {
                // Aucun filtre supplémentaire.
            }
        }

        TypedQuery<Long> query =
                entityManager.createQuery(
                        jpql,
                        Long.class
                );

        query.setParameter("start", start);
        query.setParameter("end", end);

        if (scope.isResponsable()) {

            query.setParameter(
                    "departmentId",
                    scope.departmentId()
            );

            query.setParameter(
                    "categoryCodes",
                    scope.categoryCodes()
            );
        }

        if (kind == CountKind.RESOLVED) {

            query.setParameter(
                    "resolvedStatuses",
                    Set.of(
                            TicketStatus.RESOLU,
                            TicketStatus.CLOTURE
                    )
            );
        }

        return query.getSingleResult();
    }

    private Instant monthStart(YearMonth month) {

        return month
                .atDay(1)
                .atStartOfDay(ZONE)
                .toInstant();
    }

    private List<MonthlyTicketMetric> calculateMonthlyTrend(
            Scope scope,
            YearMonth firstMonth,
            int months
    ) {

        // ==========================================
        // 1. DEFINIR LA PERIODE COMPLETE
        // ==========================================

        Instant start = monthStart(
                firstMonth
        );

        Instant end = monthStart(
                firstMonth.plusMonths(months)
        );

        // ==========================================
        // 2. UNE SEULE REQUETE POSTGRESQL
        // ==========================================

        Map<YearMonth, Long> counts =
                aggregationRepository.createdByMonth(
                        scope.departmentId(),
                        scope.categoryCodes(),
                        start,
                        end
                );

        // ==========================================
        // 3. CONSTRUIRE TOUS LES MOIS
        // ==========================================

        List<MonthlyTicketMetric> result =
                new ArrayList<>();

        for (int i = 0; i < months; i++) {

            YearMonth month =
                    firstMonth.plusMonths(i);

            // Si PostgreSQL ne retourne pas ce mois,
            // cela signifie qu'aucun ticket n'a été créé.
            long count =
                    counts.getOrDefault(
                            month,
                            0L
                    );

            // ======================================
            // 4. APPLIQUER LE MASQUAGE
            // ======================================

            boolean suppressed =
                    count > 0 && count < MIN_GROUP_SIZE;

            MonthlyTicketMetric metric =
                    new MonthlyTicketMetric();

            metric.setMonth(
                    month.toString()
            );

            metric.setSuppressed(
                    suppressed
            );

            metric.setCount(
                    suppressed ? null : count
            );

            result.add(metric);
        }

        return result;
    }

    private List<DepartmentTicketMetric> calculateDepartmentStats(
            Instant start,
            Instant end
    ) {

        List<Object[]> rows = entityManager
                .createQuery(
                        """
                        SELECT t.department.id, COUNT(t)
                        FROM Ticket t
                        WHERE t.anonymous = false
                          AND t.confidential = false
                          AND t.createdAt >= :start
                          AND t.createdAt < :end
                        GROUP BY t.department.id
                        HAVING COUNT(t) >= :minimum
                        ORDER BY COUNT(t) DESC
                        """,
                        Object[].class
                )
                .setParameter("start", start)
                .setParameter("end", end)
                .setParameter(
                        "minimum",
                        MIN_GROUP_SIZE
                )
                .getResultList();

        List<DepartmentTicketMetric> result =
                new ArrayList<>();

        for (Object[] row : rows) {

            DepartmentTicketMetric metric =
                    new DepartmentTicketMetric();

            metric.setDepartmentId(
                    (UUID) row[0]
            );

            metric.setCount(
                    ((Number) row[1]).longValue()
            );

            result.add(metric);
        }

        return result;
    }

    private Double calculatePercentage(
            long numerator,
            long denominator
    ) {

        // Pas de calcul sur un groupe trop petit.
        if (denominator < MIN_GROUP_SIZE) {
            return null;
        }

        // Masquer les résultats pouvant révéler
        // directement un très petit sous-groupe.
        if (numerator > 0 && numerator < MIN_GROUP_SIZE) {
            return null;
        }

        long remaining = denominator - numerator;

        if (remaining > 0 && remaining < MIN_GROUP_SIZE) {
            return null;
        }

        double percentage =
                numerator * 100.0 / denominator;

        return Math.round(
                percentage * 10.0
        ) / 10.0;
    }

    private DashboardAnalytics buildAnalytics(
            String scopeName,
            Scope scope,
            int months
    ) {

        // ==========================================
        // 1. DEFINIR LA PERIODE
        // ==========================================

        YearMonth currentMonth = YearMonth.now(ZONE);

        YearMonth firstMonth =
                currentMonth.minusMonths(months - 1);

        Instant start = monthStart(firstMonth);

        Instant end = monthStart(
                currentMonth.plusMonths(1)
        );


        // ==========================================
        // 2. COMPTER LES SIGNALEMENTS
        // ==========================================

        long total = countTickets(
                scope,
                start,
                end,
                CountKind.ALL
        );

        long resolved = countTickets(
                scope,
                start,
                end,
                CountKind.RESOLVED
        );

        long proposed = countTickets(
                scope,
                start,
                end,
                CountKind.PROPOSED
        );

        long reopened = countTickets(
                scope,
                start,
                end,
                CountKind.REOPENED
        );


        // ==========================================
        // 3. CONSTRUIRE LA REPONSE
        // ==========================================

        DashboardAnalytics response =
                new DashboardAnalytics();

        response.setScope(scopeName);
        response.setMonths(months);


        // ==========================================
        // 4. EVOLUTION MENSUELLE
        // ==========================================

        response.setMonthlyCreated(
                calculateMonthlyTrend(
                        scope,
                        firstMonth,
                        months
                )
        );


        // ==========================================
        // 5. TAUX DE RESOLUTION
        // ==========================================

        response.setResolutionRatePct(
                calculatePercentage(
                        resolved,
                        total
                )
        );


        // ==========================================
        // 6. TAUX DE REOUVERTURE
        // ==========================================

        response.setReopeningRatePct(
                calculatePercentage(
                        reopened,
                        proposed
                )
        );


        // ==========================================
        // 7. REPARTITION PAR DEPARTEMENT
        // ==========================================

        if ("ADMINISTRATEUR".equals(scopeName)) {

            response.setByDepartment(
                    calculateDepartmentStats(
                            start,
                            end
                    )
            );

        } else {

            response.setByDepartment(
                    List.of()
            );
        }


        // ==========================================
        // 8. DATE DE GENERATION
        // ==========================================

        response.setGeneratedAt(
                OffsetDateTime.now(
                        ZoneOffset.UTC
                )
        );

        return response;
    }
}
