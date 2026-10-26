package com.civsetu.analytics.service;

import java.sql.Types;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civsetu.analytics.config.AnalyticsProperties;
import com.civsetu.analytics.dto.AnalyticsSummaryResponse;
import com.civsetu.analytics.dto.AnalyticsSummaryResponse.CountItem;
import com.civsetu.analytics.dto.Hotspot;
import com.civsetu.analytics.dto.OverdueIssue;
import com.civsetu.analytics.dto.TrendPoint;
import com.civsetu.common.error.ApiException;
import com.civsetu.common.security.Caller;
import com.civsetu.common.web.PageResponse;

/**
 * Read-only reporting. This is the one feature that reads other features' tables directly, with
 * SQL aggregates, because reports need joins and counts the services do not offer. It never writes.
 * Every query is limited to the caller's scope: Admin the whole city, an Authority their
 * department, a Citizen their own reports.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private static final String OPEN = "('REPORTED', 'ASSIGNED', 'IN_PROGRESS')";
    private static final String SCOPE =
            " AND (CAST(:departmentId AS uuid) IS NULL OR i.department_id = CAST(:departmentId AS uuid))"
                    + " AND (CAST(:reporterId AS uuid) IS NULL OR i.reporter_id = CAST(:reporterId AS uuid))";
    private static final Duration DEFAULT_PERIOD = Duration.ofDays(30);
    private static final Duration MAX_PERIOD = Duration.ofDays(366);
    // About 500 m at Indian latitudes
    private static final double HOTSPOT_CELL_DEGREES = 0.005;

    private final NamedParameterJdbcTemplate jdbc;
    private final AnalyticsProperties properties;
    private final Clock clock;

    AnalyticsService(NamedParameterJdbcTemplate jdbc, AnalyticsProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    private record Scope(String name, UUID departmentId, UUID reporterId) {

        static Scope of(Caller caller) {
            if (caller.isAdmin()) {
                return new Scope("CITY", null, null);
            }
            if (caller.departmentId() != null) {
                return new Scope("DEPARTMENT", caller.departmentId(), null);
            }
            return new Scope("MY_REPORTS", null, caller.id());
        }

        MapSqlParameterSource params() {
            return new MapSqlParameterSource()
                    .addValue("departmentId", departmentId, Types.OTHER)
                    .addValue("reporterId", reporterId, Types.OTHER);
        }
    }

    public AnalyticsSummaryResponse summary(Caller caller, Instant from, Instant to) {
        Scope scope = Scope.of(caller);
        Instant end = to == null ? clock.instant() : to;
        Instant start = from == null ? end.minus(DEFAULT_PERIOD) : from;
        checkPeriod(start, end);
        MapSqlParameterSource params = scope.params()
                .addValue("from", utc(start))
                .addValue("to", utc(end))
                .addValue("now", utc(clock.instant()));
        String reportedInPeriod = " FROM issues i WHERE i.created_at >= :from AND i.created_at < :to" + SCOPE;

        Map<String, Long> byStatus = countsBy("i.status", reportedInPeriod, params);
        Map<String, Long> byPriority = countsBy("i.priority", reportedInPeriod, params);
        List<CountItem> byCategory = items("SELECT c.code, c.label, count(*) FROM issues i "
                + "JOIN categories c ON c.id = i.category_id WHERE i.created_at >= :from AND i.created_at < :to"
                + SCOPE + " GROUP BY c.code, c.label ORDER BY count(*) DESC, c.code", params);
        List<CountItem> byDepartment = items("SELECT d.code, d.name, count(*) FROM issues i "
                + "JOIN departments d ON d.id = i.department_id WHERE i.created_at >= :from AND i.created_at < :to"
                + SCOPE + " GROUP BY d.code, d.name ORDER BY count(*) DESC, d.code", params);

        Map<String, Object> resolution = jdbc.queryForMap("""
                SELECT count(*) AS resolved,
                       avg(extract(epoch FROM (i.resolved_at - i.created_at))) / 3600 AS average_hours,
                       percentile_cont(0.5) WITHIN GROUP (
                           ORDER BY extract(epoch FROM (i.resolved_at - i.created_at))) / 3600 AS median_hours,
                       count(*) FILTER (WHERE i.resolved_at <= i.sla_due_at) AS within_deadline
                FROM issues i
                WHERE i.status = 'RESOLVED' AND i.resolved_at >= :from AND i.resolved_at < :to""" + SCOPE, params);
        long resolved = ((Number) resolution.get("resolved")).longValue();
        long withinDeadline = ((Number) resolution.get("within_deadline")).longValue();

        Map<String, Object> open = jdbc.queryForMap("SELECT count(*) AS open_now, "
                + "count(*) FILTER (WHERE i.sla_due_at < :now) AS overdue_now FROM issues i WHERE i.status IN "
                + OPEN + SCOPE, params);

        return new AnalyticsSummaryResponse(scope.name(), start, end,
                byStatus.values().stream().mapToLong(Long::longValue).sum(), byStatus, byPriority, byCategory,
                byDepartment, resolved, rounded(resolution.get("average_hours")),
                rounded(resolution.get("median_hours")), withinDeadline,
                resolved == 0 ? null : Math.round(withinDeadline * 1000.0 / resolved) / 10.0,
                ((Number) open.get("open_now")).longValue(), ((Number) open.get("overdue_now")).longValue());
    }

    /** Reported and resolved per day for the last {@code days} days, today included, in the city's time zone. */
    public List<TrendPoint> trend(Caller caller, int days) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), properties.timeZone());
        LocalDate first = today.minusDays(days - 1L);
        MapSqlParameterSource params = Scope.of(caller).params()
                .addValue("from", first.atStartOfDay(properties.timeZone()).toOffsetDateTime())
                .addValue("tz", properties.timeZone().getId());
        Map<LocalDate, Long> reported = dailyCounts("SELECT (i.created_at AT TIME ZONE :tz)::date, count(*) "
                + "FROM issues i WHERE i.created_at >= :from" + SCOPE + " GROUP BY 1", params);
        Map<LocalDate, Long> resolved = dailyCounts("SELECT (i.resolved_at AT TIME ZONE :tz)::date, count(*) "
                + "FROM issues i WHERE i.status = 'RESOLVED' AND i.resolved_at >= :from" + SCOPE + " GROUP BY 1",
                params);
        List<TrendPoint> points = new ArrayList<>();
        for (LocalDate day = first; !day.isAfter(today); day = day.plusDays(1)) {
            points.add(new TrendPoint(day, reported.getOrDefault(day, 0L), resolved.getOrDefault(day, 0L)));
        }
        return points;
    }

    /** The map cells with the most reports in the period. */
    public List<Hotspot> hotspots(Caller caller, Instant from, Instant to, int limit) {
        Instant end = to == null ? clock.instant() : to;
        Instant start = from == null ? end.minus(DEFAULT_PERIOD) : from;
        checkPeriod(start, end);
        MapSqlParameterSource params = Scope.of(caller).params()
                .addValue("from", utc(start))
                .addValue("to", utc(end))
                .addValue("cell", HOTSPOT_CELL_DEGREES)
                .addValue("limit", limit);
        return jdbc.query("""
                SELECT ST_Y(ST_Centroid(ST_Collect(i.location))) AS lat,
                       ST_X(ST_Centroid(ST_Collect(i.location))) AS lng,
                       count(*) AS reported,
                       count(*) FILTER (WHERE i.status IN %s) AS still_open
                FROM issues i
                WHERE i.created_at >= :from AND i.created_at < :to%s
                GROUP BY ST_SnapToGrid(i.location, :cell)
                ORDER BY reported DESC
                LIMIT :limit""".formatted(OPEN, SCOPE), params,
                (row, i) -> new Hotspot(row.getDouble("lat"), row.getDouble("lng"), row.getLong("reported"),
                        row.getLong("still_open")));
    }

    /** Open issues past their deadline, most overdue first. */
    public PageResponse<OverdueIssue> overdue(Caller caller, int page, int size) {
        MapSqlParameterSource params = Scope.of(caller).params()
                .addValue("now", utc(clock.instant()))
                .addValue("limit", size)
                .addValue("offset", (long) page * size);
        String where = " FROM issues i JOIN departments d ON d.id = i.department_id WHERE i.status IN " + OPEN
                + " AND i.sla_due_at < :now" + SCOPE;
        long total = jdbc.queryForObject("SELECT count(*)" + where, params, Long.class);
        List<OverdueIssue> content = jdbc.query("SELECT i.id, i.title, d.name, i.priority, i.sla_due_at, "
                        + "extract(epoch FROM (:now - i.sla_due_at)) / 3600 AS hours" + where
                        + " ORDER BY i.sla_due_at, i.id LIMIT :limit OFFSET :offset", params,
                (row, i) -> new OverdueIssue(row.getObject("id", UUID.class), row.getString("title"),
                        row.getString("name"), row.getString("priority"),
                        row.getObject("sla_due_at", OffsetDateTime.class).toInstant(),
                        Math.round(row.getDouble("hours") * 10) / 10.0));
        return new PageResponse<>(content, page, size, total, (int) ((total + size - 1) / size));
    }

    /** Count per value of one column, e.g. per status. */
    private Map<String, Long> countsBy(String column, String fromWhere, MapSqlParameterSource params) {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query("SELECT " + column + ", count(*)" + fromWhere + " GROUP BY " + column + " ORDER BY " + column,
                params, row -> {
                    counts.put(row.getString(1), row.getLong(2));
                });
        return counts;
    }

    private List<CountItem> items(String sql, MapSqlParameterSource params) {
        return jdbc.query(sql, params, (row, i) -> new CountItem(row.getString(1), row.getString(2), row.getLong(3)));
    }

    private Map<LocalDate, Long> dailyCounts(String sql, MapSqlParameterSource params) {
        Map<LocalDate, Long> counts = new HashMap<>();
        jdbc.query(sql, params, row -> {
            counts.put(row.getObject(1, LocalDate.class), row.getLong(2));
        });
        return counts;
    }

    private static void checkPeriod(Instant from, Instant to) {
        if (!from.isBefore(to) || Duration.between(from, to).compareTo(MAX_PERIOD) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PERIOD",
                    "'from' must be before 'to', and the period at most " + MAX_PERIOD.toDays() + " days.");
        }
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Double rounded(Object hours) {
        return hours == null ? null : Math.round(((Number) hours).doubleValue() * 10) / 10.0;
    }
}
