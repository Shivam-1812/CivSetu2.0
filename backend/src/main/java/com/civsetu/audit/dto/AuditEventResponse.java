package com.civsetu.audit.dto;

import java.time.Instant;
import java.util.UUID;

import com.civsetu.audit.entity.AuditEvent;

public record AuditEventResponse(UUID id, UUID actorId, String actorRole, String action, String entityType,
        UUID entityId, String details, Instant createdAt) {

    public static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(event.getId(), event.getActorId(), event.getActorRole(), event.getAction(),
                event.getEntityType(), event.getEntityId(), event.getDetails(), event.getCreatedAt());
    }
}
