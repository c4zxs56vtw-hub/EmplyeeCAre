package com.employeecare.api.statistics.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Repository
public class DashboardAggregationRepository {

    private final NamedParameterJdbcTemplate jdbc;

    @Autowired
    public DashboardAggregationRepository(
            DataSource dataSource
    ) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    DashboardAggregationRepository(
            NamedParameterJdbcTemplate jdbc
    ) {
        this.jdbc = jdbc;
    }

    // ==========================================
    // RESULTAT DES DELAIS MOYENS
    // ==========================================

    public record Delays(
            Double takeChargeHours,
            Double closureHours
    ) {
    }

    // ==========================================
    // FILTRE DES PERMISSIONS
    // ==========================================

    private String scopeFilter(UUID departmentId) {

        if (departmentId == null) {
            return "";
        }

        return """
                  AND t.department_id = :departmentId
                  AND t.category_code IN (:categoryCodes)
                """;
    }

    private MapSqlParameterSource scopeParameters(
            UUID departmentId,
            Set<String> categoryCodes
    ) {

        MapSqlParameterSource params =
                new MapSqlParameterSource();

        if (departmentId != null) {

            params.addValue(
                    "departmentId",
                    departmentId
            );

            params.addValue(
                    "categoryCodes",
                    categoryCodes
            );
        }

        return params;
    }

    // ==========================================
    // OPTIMISATION 1 :
    // CALCUL DES DELAIS DANS POSTGRESQL
    // ==========================================

    public Delays averageDurations(
            UUID departmentId,
            Set<String> categoryCodes
    ) {

        // Un responsable sans catégorie
        // ne possède aucune donnée autorisée.

        if (departmentId != null
                && (categoryCodes == null
                || categoryCodes.isEmpty())) {

            return new Delays(null, null);
        }

        String sql = """
                WITH events_by_ticket AS (
                    SELECT
                        t.id,
                        t.created_at,
                        t.status,

                        MIN(e.created_at) FILTER (
                            WHERE e.event_type = 'PRISE_EN_CHARGE'
                        ) AS first_taken_at,

                        MAX(e.created_at) FILTER (
                            WHERE e.event_type = 'CLOTURE_SIGNALEMENT'
                        ) AS last_closed_at

                    FROM tickets t

                    LEFT JOIN ticket_events e
                        ON e.ticket_id = t.id
                       AND e.event_type IN (
                           'PRISE_EN_CHARGE',
                           'CLOTURE_SIGNALEMENT'
                       )

                    WHERE t.is_anonymous = FALSE
                      AND t.is_confidential = FALSE
                """
                + scopeFilter(departmentId)
                + """
                    GROUP BY
                        t.id,
                        t.created_at,
                        t.status
                )

                SELECT

                    ROUND(
                        AVG(
                            EXTRACT(
                                EPOCH FROM (
                                    first_taken_at - created_at
                                )
                            ) / 3600.0
                        ) FILTER (
                            WHERE first_taken_at IS NOT NULL
                              AND first_taken_at >= created_at
                        ),
                        2
                    ) AS take_charge_hours,

                    ROUND(
                        AVG(
                            EXTRACT(
                                EPOCH FROM (
                                    last_closed_at - created_at
                                )
                            ) / 3600.0
                        ) FILTER (
                            WHERE status = 'CLOTURE'
                              AND last_closed_at IS NOT NULL
                              AND last_closed_at >= created_at
                        ),
                        2
                    ) AS closure_hours

                FROM events_by_ticket
                """;

        MapSqlParameterSource params =
                scopeParameters(
                        departmentId,
                        categoryCodes
                );

        return jdbc.queryForObject(
                sql,
                params,
                (rs, rowNum) -> {

                    BigDecimal takeCharge =
                            rs.getBigDecimal(
                                    "take_charge_hours"
                            );

                    BigDecimal closure =
                            rs.getBigDecimal(
                                    "closure_hours"
                            );

                    return new Delays(
                            takeCharge == null
                                    ? null
                                    : takeCharge.doubleValue(),
                            closure == null
                                    ? null
                                    : closure.doubleValue()
                    );
                }
        );
    }

    // ==========================================
    // OPTIMISATION 2 :
    // EVOLUTION MENSUELLE EN UNE REQUETE
    // ==========================================

    public Map<YearMonth, Long> createdByMonth(
            UUID departmentId,
            Set<String> categoryCodes,
            Instant start,
            Instant end
    ) {

        if (departmentId != null
                && (categoryCodes == null
                || categoryCodes.isEmpty())) {

            return Map.of();
        }

        String sql = """
                SELECT
                    TO_CHAR(
                        DATE_TRUNC(
                            'month',
                            t.created_at AT TIME ZONE 'Africa/Douala'
                        ),
                        'YYYY-MM'
                    ) AS month_key,

                    COUNT(*) AS total

                FROM tickets t

                WHERE t.is_anonymous = FALSE
                  AND t.is_confidential = FALSE
                  AND t.created_at >= :startDate
                  AND t.created_at < :endDate
                """
                + scopeFilter(departmentId)
                + """
                GROUP BY 1
                ORDER BY 1
                """;

        MapSqlParameterSource params =
                scopeParameters(
                        departmentId,
                        categoryCodes
                );

        params.addValue(
                "startDate",
                OffsetDateTime.ofInstant(
                        start,
                        ZoneOffset.UTC
                )
        );

        params.addValue(
                "endDate",
                OffsetDateTime.ofInstant(
                        end,
                        ZoneOffset.UTC
                )
        );

        Map<YearMonth, Long> result =
                new HashMap<>();

        jdbc.query(
                sql,
                params,
                rs -> {

                    YearMonth month =
                            YearMonth.parse(
                                    rs.getString("month_key")
                            );

                    long count =
                            rs.getLong("total");

                    result.put(month, count);
                }
        );

        return result;
    }
}
