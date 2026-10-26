package com.civsetu.analytics.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Figures for issues reported in [from, to), within the caller's scope.
 *
 * @param scope                  CITY (Admin), DEPARTMENT (Authority) or MY_REPORTS (Citizen)
 * @param resolvedWithinDeadline issues resolved in the period on or before their deadline
 * @param slaCompliancePercent   share of issues resolved in the period that met their deadline
 * @param openNow                open issues right now (not limited to the period)
 * @param openOverdueNow         open issues past their deadline right now
 */
public record AnalyticsSummaryResponse(
        String scope,
        Instant from,
        Instant to,
        long reported,
        Map<String, Long> byStatus,
        Map<String, Long> byPriority,
        List<CountItem> byCategory,
        List<CountItem> byDepartment,
        long resolved,
        Double averageResolutionHours,
        Double medianResolutionHours,
        long resolvedWithinDeadline,
        Double slaCompliancePercent,
        long openNow,
        long openOverdueNow) {

    public record CountItem(String key, String label, long count) {
    }
}
