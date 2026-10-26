package com.civsetu.analytics.controller;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.civsetu.analytics.dto.AnalyticsSummaryResponse;
import com.civsetu.analytics.dto.Hotspot;
import com.civsetu.analytics.dto.OverdueIssue;
import com.civsetu.analytics.dto.TrendPoint;
import com.civsetu.analytics.service.AnalyticsService;
import com.civsetu.common.security.Caller;
import com.civsetu.common.web.PageResponse;

/**
 * Dashboard figures, scoped to the caller: Admin the whole city, an Authority their department,
 * a Citizen their own reports. Periods default to the last 30 days.
 */
@RestController
@RequestMapping("/api/v1/analytics")
class AnalyticsController {

    private final AnalyticsService analytics;

    AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/summary")
    AnalyticsSummaryResponse summary(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return analytics.summary(Caller.from(jwt), from, to);
    }

    @GetMapping("/trend")
    List<TrendPoint> trend(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "30") @Min(1) @Max(90) int days) {
        return analytics.trend(Caller.from(jwt), days);
    }

    @GetMapping("/hotspots")
    List<Hotspot> hotspots(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return analytics.hotspots(Caller.from(jwt), from, to, limit);
    }

    @GetMapping("/overdue")
    PageResponse<OverdueIssue> overdue(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return analytics.overdue(Caller.from(jwt), page, size);
    }
}
