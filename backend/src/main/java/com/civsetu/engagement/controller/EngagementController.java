package com.civsetu.engagement.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.civsetu.common.security.Caller;
import com.civsetu.common.web.PageResponse;
import com.civsetu.engagement.dto.CommentRequest;
import com.civsetu.engagement.dto.CommentResponse;
import com.civsetu.engagement.dto.VoteRequest;
import com.civsetu.engagement.dto.VoteTally;
import com.civsetu.engagement.service.EngagementService;

/**
 * Votes (citizens only, one per issue) and comments (anyone signed in; reading is public).
 */
@RestController
@RequestMapping("/api/v1/issues/{id}")
class EngagementController {

    private final EngagementService engagement;

    EngagementController(EngagementService engagement) {
        this.engagement = engagement;
    }

    /** Sets or changes the caller's vote; safe to repeat. */
    @PutMapping("/vote")
    @PreAuthorize("hasRole('CITIZEN')")
    VoteTally vote(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody VoteRequest request) {
        return engagement.vote(id, Caller.from(jwt), request.value());
    }

    @DeleteMapping("/vote")
    @PreAuthorize("hasRole('CITIZEN')")
    VoteTally removeVote(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return engagement.removeVote(id, Caller.from(jwt));
    }

    @GetMapping("/votes")
    VoteTally votes(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return engagement.tally(id, Caller.from(jwt));
    }

    @GetMapping("/comments")
    PageResponse<CommentResponse> comments(@PathVariable UUID id, Pageable pageable) {
        return engagement.comments(id, pageable);
    }

    @PostMapping("/comments")
    @ResponseStatus(HttpStatus.CREATED)
    CommentResponse comment(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CommentRequest request) {
        return engagement.addComment(id, Caller.from(jwt), request.body());
    }
}
