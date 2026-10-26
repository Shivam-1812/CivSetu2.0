package com.civsetu.engagement.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.civsetu.common.security.Caller;
import com.civsetu.engagement.dto.CommentResponse;
import com.civsetu.engagement.dto.ModerateCommentRequest;
import com.civsetu.engagement.service.EngagementService;

/**
 * Comment moderation: hide abusive or off-topic comments. This touches only comments, never an
 * issue's routing, priority or status. Every action is audited and the reason is public.
 */
@RestController
@RequestMapping("/api/v1/admin/comments")
@PreAuthorize("hasRole('ADMIN')")
class AdminCommentController {

    private final EngagementService engagement;

    AdminCommentController(EngagementService engagement) {
        this.engagement = engagement;
    }

    @PatchMapping("/{commentId}")
    CommentResponse moderate(@PathVariable UUID commentId, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ModerateCommentRequest request) {
        return engagement.moderate(commentId, Caller.from(jwt), request);
    }
}
