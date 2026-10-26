package com.civsetu.notification.dto;

import java.time.Instant;
import java.util.UUID;

import com.civsetu.notification.entity.Notification;
import com.civsetu.notification.entity.NotificationType;

public record NotificationResponse(UUID id, NotificationType type, String title, String body, UUID issueId,
        boolean read, Instant createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(), notification.getTitle(),
                notification.getBody(), notification.getIssueId(), notification.getReadAt() != null,
                notification.getCreatedAt());
    }
}
