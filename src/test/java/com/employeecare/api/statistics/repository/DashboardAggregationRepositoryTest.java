package com.employeecare.api.statistics.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardAggregationRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    @Test
    @DisplayName("averageDurations - Sans catégorie pour un responsable retourne null sans exécuter de requête SQL")
    void averageDurations_whenResponsableWithoutCategories_shouldReturnNullDelaysWithoutQuery() {
        DashboardAggregationRepository repository = new DashboardAggregationRepository(jdbc);

        DashboardAggregationRepository.Delays delays =
                repository.averageDurations(UUID.randomUUID(), Set.of());

        assertThat(delays.takeChargeHours()).isNull();
        assertThat(delays.closureHours()).isNull();
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("averageDurations - Mappe les moyennes retournées par PostgreSQL")
    @SuppressWarnings("unchecked")
    void averageDurations_shouldMapSqlResult() throws Exception {
        DashboardAggregationRepository repository = new DashboardAggregationRepository(jdbc);

        when(jdbc.queryForObject(
                contains("WITH events_by_ticket AS"),
                any(MapSqlParameterSource.class),
                any(RowMapper.class)
        )).thenAnswer(invocation -> {
            RowMapper<DashboardAggregationRepository.Delays> mapper = invocation.getArgument(2);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getBigDecimal("take_charge_hours")).thenReturn(new BigDecimal("2.00"));
            when(rs.getBigDecimal("closure_hours")).thenReturn(new BigDecimal("14.50"));
            return mapper.mapRow(rs, 0);
        });

        DashboardAggregationRepository.Delays delays =
                repository.averageDurations(null, Set.of());

        assertThat(delays.takeChargeHours()).isEqualTo(2.0);
        assertThat(delays.closureHours()).isEqualTo(14.5);
    }

    @Test
    @DisplayName("createdByMonth - Agrège les totaux par YearMonth en une seule requête")
    void createdByMonth_shouldAggregateCountsByYearMonth() {
        DashboardAggregationRepository repository = new DashboardAggregationRepository(jdbc);

        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(2);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("month_key")).thenReturn("2026-06", "2026-08");
            when(rs.getLong("total")).thenReturn(8L, 10L);
            handler.processRow(rs);
            handler.processRow(rs);
            return null;
        }).when(jdbc).query(
                contains("DATE_TRUNC"),
                any(MapSqlParameterSource.class),
                any(RowCallbackHandler.class)
        );

        Map<YearMonth, Long> result = repository.createdByMonth(
                null,
                Set.of(),
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-09-01T00:00:00Z")
        );

        assertThat(result)
                .hasSize(2)
                .containsEntry(YearMonth.of(2026, 6), 8L)
                .containsEntry(YearMonth.of(2026, 8), 10L);
    }
}
