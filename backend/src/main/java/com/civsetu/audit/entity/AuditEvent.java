package com.civsetu.audit.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.civsetu.common.persistence.BaseEntity;

/**
 * One recorded action. A null actor means the system did it.
 */
@Entity
@Table(name = "audit_events")
public class AuditEvent extends BaseEntity {

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(name = "actor_role", nullable = false, length = 20, updatable = false)
    private String actorRole;

    @Column(nullable = false, length = 60, updatable = false)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 40, updatable = false)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private UUID entityId;

    @Column(columnDefinition = "text", updatable = false)
    private String details;

    protected AuditEvent() {
    }

    public AuditEvent(UUID actorId, String actorRole, String action, String entityType, UUID entityId,
            String details) {
        this.actorId = actorId;
        this.actorRole = actorRole;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getActorRole() {
        return actorRole;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getDetails() {
        return details;
    }
}
