package com.civsetu.notification.controller;

import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.civsetu.common.security.Caller;
import com.civsetu.common.web.PageResponse;
import com.civsetu.notification.dto.NotificationResponse;
import com.civsetu.notification.dto.StreamTicketResponse;
import com.civsetu.notification.service.NotificationService;

/**
 * The caller's notifications. Live updates: POST /stream-ticket (with the access token), then open
 * {@code new EventSource(streamUrl)}; events are named "notification" and carry the same JSON as the list.
 */
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController {

    private final NotificationService notifications;

    NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    PageResponse<NotificationResponse> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "false") boolean unreadOnly, Pageable pageable) {
        return notifications.list(Caller.from(jwt), unreadOnly, pageable);
    }

    @GetMapping("/unread-count")
    Map<String, Long> unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("count", notifications.unreadCount(Caller.from(jwt)));
    }

    @PatchMapping("/{id}/read")
    NotificationResponse markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return notifications.markRead(Caller.from(jwt), id);
    }

    @PostMapping("/read-all")
    Map<String, Integer> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("updated", notifications.markAllRead(Caller.from(jwt)));
    }

    @PostMapping("/stream-ticket")
    StreamTicketResponse streamTicket(@AuthenticationPrincipal Jwt jwt) {
        return notifications.streamTicket(Caller.from(jwt));
    }

    /** Server-Sent Events. Authenticated by the one-time ticket, because EventSource cannot send headers. */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(@RequestParam(required = false) String ticket,
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId,
            HttpServletResponse response) {
        // Tell reverse proxies (nginx, Caddy) not to buffer the stream
        response.setHeader("X-Accel-Buffering", "no");
        return notifications.openStream(ticket, lastEventId);
    }
}
