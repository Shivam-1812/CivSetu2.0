package com.civsetu.survey.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.civsetu.common.security.Caller;
import com.civsetu.issue.dto.IssueCluster;
import com.civsetu.survey.dto.CreateSurveyRequest;
import com.civsetu.survey.dto.SubmitAnswersRequest;
import com.civsetu.survey.dto.SurveyDetailResponse;
import com.civsetu.survey.dto.SurveyResultsResponse;
import com.civsetu.survey.service.SurveyService;

@RestController
@RequestMapping("/api/v1/surveys")
class SurveyController {

    private final SurveyService surveys;

    SurveyController(SurveyService surveys) {
        this.surveys = surveys;
    }

    /** Areas with several open issues (an Authority sees its department's issues only). */
    @GetMapping("/clusters")
    @PreAuthorize("hasAnyRole('AUTHORITY', 'ADMIN')")
    List<IssueCluster> clusters(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "500") @Min(50) @Max(5000) int radius,
            @RequestParam(defaultValue = "3") @Min(2) @Max(100) int minIssues) {
        return surveys.clusters(Caller.from(jwt), radius, minIssues);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('AUTHORITY', 'ADMIN')")
    SurveyDetailResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateSurveyRequest request) {
        return surveys.create(Caller.from(jwt), request);
    }

    /** Citizens: surveys I am invited to. */
    @GetMapping("/mine")
    @PreAuthorize("hasRole('CITIZEN')")
    List<SurveyDetailResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return surveys.invitations(Caller.from(jwt));
    }

    @GetMapping("/{id}")
    SurveyDetailResponse get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return surveys.get(id, Caller.from(jwt));
    }

    @PostMapping("/{id}/responses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CITIZEN')")
    SurveyDetailResponse respond(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SubmitAnswersRequest request) {
        return surveys.respond(id, Caller.from(jwt), request);
    }

    @GetMapping("/{id}/results")
    @PreAuthorize("hasAnyRole('AUTHORITY', 'ADMIN')")
    SurveyResultsResponse results(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return surveys.results(id, Caller.from(jwt));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('AUTHORITY', 'ADMIN')")
    SurveyDetailResponse close(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return surveys.close(id, Caller.from(jwt));
    }
}
