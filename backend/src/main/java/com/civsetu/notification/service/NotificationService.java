package com.civsetu.notification.service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.civsetu.common.error.ApiException;
import com.civsetu.common.security.Caller;
import com.civsetu.common.web.PageResponse;
import com.civsetu.notification.config.NotificationProperties;
import com.civsetu.notification.dto.NotificationResponse;
import com.civsetu.notification.dto.StreamTicketResponse;
import com.civsetu.notification.entity.Notification;
import com.civsetu.notification.entity.NotificationType;
import com.civsetu.notification.repository.NotificationRepository;

/**
 * In-app notifications: stored first (so nothing is lost while a user is offline), then pushed
 * live to any open stream once the change that caused them is committed.
 */
@Service
@Transactional(readOnly = true)
public class NotificationService {

    private static final int MAX_REPLAY = 50;
    private static final int MAX_BODY = 1000;

    private final NotificationRepository notifications;
    private final NotificationStreams streams;
    private final StreamTickets tickets;
    private final NotificationProperties properties;
    private final Clock clock;

    NotificationService(NotificationRepository notifications, NotificationStreams streams, StreamTickets tickets,
            NotificationProperties properties, Clock clock) {
        this.notifications = notifications;
        this.streams = streams;
        this.tickets = tickets;
        this.properties = properties;
        this.clock = clock;
    }

    public PageResponse<NotificationResponse> list(Caller user, boolean unreadOnly, Pageable pageable) {
        Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "seq"));
        Page<Notification> page = unreadOnly
                ? notifications.findByUserIdAndReadAtIsNull(user.id(), newestFirst)
                : notifications.findByUserId(user.id(), newestFirst);
        return PageResponse.of(page, NotificationResponse::from);
    }

    public long unreadCount(Caller user) {
        return notifications.countByUserIdAndReadAtIsNull(user.id());
    }

    @Transactional
    public NotificationResponse markRead(Caller user, UUID notificationId) {
        // Someone else's notification looks the same as a missing one
        Notification notification = notifications.findByIdAndUserId(notificationId, user.id())
                .orElseThrow(() -> ApiException.notFound("Notification"));
        notification.markRead(clock.instant());
        return NotificationResponse.from(notification);
    }

    @Transactional
    public int markAllRead(Caller user) {
        return notifications.markAllRead(user.id(), clock.instant());
    }

    public StreamTicketResponse streamTicket(Caller user) {
        String ticket = tickets.issue(user.id());
        return new StreamTicketResponse(ticket, properties.ticketTtl().toSeconds(),
                "/api/v1/notifications/stream?ticket=" + ticket);
    }

    /**
     * Opens the live stream for a ticket. With {@code lastEventId} (sent by the browser when it
     * reconnects) the notifications created after that one are replayed first.
     */
    public SseEmitter openStream(String ticket, String lastEventId) {
        UUID userId = tickets.redeem(ticket).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,
                "INVALID_STREAM_TICKET", "The stream ticket is missing, expired or already used."));
        return streams.open(userId, missedSince(userId, lastEventId));
    }

    /** Stores a notification in the current transaction and pushes it after commit. */
    @Transactional
    void notify(UUID userId, NotificationType type, String title, String body, UUID issueId) {
        String text = body.length() <= MAX_BODY ? body : body.substring(0, MAX_BODY - 1) + "…";
        NotificationResponse saved = NotificationResponse.from(
                notifications.save(new Notification(userId, type, title, text, issueId)));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    streams.push(userId, saved);
                }
            });
        } else {
            streams.push(userId, saved);
        }
    }

    private List<NotificationResponse> missedSince(UUID userId, String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return List.of();
        }
        try {
            return notifications.findByIdAndUserId(UUID.fromString(lastEventId.trim()), userId)
                    .map(last -> notifications.findByUserIdAndSeqGreaterThanOrderBySeqAsc(userId, last.getSeq(),
                            Limit.of(MAX_REPLAY)))
                    .orElse(List.of())
                    .stream()
                    .map(NotificationResponse::from)
                    .toList();
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }
}
