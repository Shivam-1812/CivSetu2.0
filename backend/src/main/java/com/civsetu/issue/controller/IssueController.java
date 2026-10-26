package com.civsetu.issue.controller;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.civsetu.common.security.Caller;
import com.civsetu.common.web.PageResponse;
import com.civsetu.issue.dto.CreateIssueRequest;
import com.civsetu.issue.dto.IssueDetailResponse;
import com.civsetu.issue.dto.IssueSearchParams;
import com.civsetu.issue.dto.IssueSummaryResponse;
import com.civsetu.issue.dto.MapResponse;
import com.civsetu.issue.service.IssueService;

/**
 * Reporting and browsing issues. Browsing is public (no token needed); a token, when sent,
 * unlocks the reporter- and staff-only fields of an issue.
 */
@RestController
@RequestMapping("/api/v1/issues")
class IssueController {

    private final IssueService issues;

    IssueController(IssueService issues) {
        this.issues = issues;
    }

    /** Citizens only: report a new issue. */
    @PostMapping
    @PreAuthorize("hasRole('CITIZEN')")
    ResponseEntity<IssueDetailResponse> report(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateIssueRequest request) {
        IssueDetailResponse created = issues.report(Caller.from(jwt), request);
        return ResponseEntity.created(URI.create("/api/v1/issues/" + created.id())).body(created);
    }

    /** Filtered, paginated list, newest first. */
    @GetMapping
    PageResponse<IssueSummaryResponse> search(@Valid IssueSearchParams params, Pageable pageable) {
        return issues.search(params, pageable);
    }

    /** Markers for a map viewport ({@code bbox} required). Accepts the same filters as the list. */
    @GetMapping("/map")
    MapResponse map(@Valid IssueSearchParams params) {
        return issues.map(params);
    }

    /** The caller's own reports. */
    @GetMapping("/mine")
    PageResponse<IssueSummaryResponse> mine(@AuthenticationPrincipal Jwt jwt, Pageable pageable) {
        return issues.reportedBy(Caller.from(jwt), pageable);
    }

    @GetMapping("/{id}")
    IssueDetailResponse get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return issues.get(id, Caller.from(jwt));
    }
}
