package com.civsetu.issue.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.civsetu.common.security.Caller;
import com.civsetu.issue.dto.DuplicateRequest;
import com.civsetu.issue.dto.HistoryEntryResponse;
import com.civsetu.issue.dto.IssueDetailResponse;
import com.civsetu.issue.dto.PlanRequest;
import com.civsetu.issue.dto.RejectRequest;
import com.civsetu.issue.dto.ReopenRequest;
import com.civsetu.issue.dto.ResolveRequest;
import com.civsetu.issue.dto.ReturnRequest;
import com.civsetu.issue.service.IssueWorkflowService;

/**
 * Workflow actions after automatic routing. There is deliberately no endpoint to assign, prioritise
 * or reschedule an issue: those are decided by the system only.
 *
 * <p>Plan, resolve and reject take {@code multipart/form-data}: a {@code data} part (JSON, Content-Type
 * application/json) plus optional files. Return and reopen take plain JSON.
 */
@RestController
@RequestMapping("/api/v1/issues/{id}")
class IssueWorkflowController {

    private final IssueWorkflowService workflow;

    IssueWorkflowController(IssueWorkflowService workflow) {
        this.workflow = workflow;
    }

    /** Assigned Authority: start work with a plan. Optional {@code documents} (images or PDF). */
    @PostMapping(path = "/plan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('AUTHORITY')")
    IssueDetailResponse plan(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestPart("data") PlanRequest request,
            @RequestPart(name = "documents", required = false) List<MultipartFile> documents) {
        return workflow.plan(id, Caller.from(jwt), request, documents);
    }

    /** Assigned Authority: mark fixed. At least one {@code proofs} photo is required. */
    @PostMapping(path = "/resolve", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('AUTHORITY')")
    IssueDetailResponse resolve(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestPart("data") ResolveRequest request,
            @RequestPart(name = "proofs", required = false) List<MultipartFile> proofs) {
        return workflow.resolve(id, Caller.from(jwt), request, proofs);
    }

    /** Assigned Authority: close without fixing, with a reason. Optional {@code evidence} photos. */
    @PostMapping(path = "/reject", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('AUTHORITY')")
    IssueDetailResponse reject(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestPart("data") RejectRequest request,
            @RequestPart(name = "evidence", required = false) List<MultipartFile> evidence) {
        return workflow.reject(id, Caller.from(jwt), request, evidence);
    }

    /** Assigned Authority: not our department; the system re-routes it elsewhere. */
    @PostMapping(path = "/return", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('AUTHORITY')")
    IssueDetailResponse returnForRerouting(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReturnRequest request) {
        return workflow.returnForRerouting(id, Caller.from(jwt), request);
    }

    /** Assigned Authority: close as a duplicate of another report. */
    @PostMapping(path = "/duplicate", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('AUTHORITY')")
    IssueDetailResponse markDuplicate(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DuplicateRequest request) {
        return workflow.markDuplicate(id, Caller.from(jwt), request);
    }

    /** Reporter: reopen a resolved, rejected or duplicate issue. */

    @PostMapping(path = "/reopen", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('CITIZEN')")
    IssueDetailResponse reopen(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReopenRequest request) {
        return workflow.reopen(id, Caller.from(jwt), request);
    }

    /** Public timeline of every step, oldest first. */
    @GetMapping("/history")
    List<HistoryEntryResponse> history(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return workflow.history(id, Caller.from(jwt));
    }
}
