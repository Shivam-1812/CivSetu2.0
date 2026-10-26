package com.civsetu.audit.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civsetu.audit.dto.AuditEventResponse;
import com.civsetu.audit.entity.AuditEvent;
import com.civsetu.audit.repository.AuditEventRepository;
import com.civsetu.common.security.Caller;
import com.civsetu.common.web.PageResponse;

/**
 * Records who did what. Entries are written in the same transaction as the change they
 * describe, so a change and its audit entry are saved together or not at all.
 */
@Service
@Transactional(readOnly = true)
public class AuditService {

    public static final String SYSTEM = "SYSTEM";

    private final AuditEventRepository events;

    AuditService(AuditEventRepository events) {
        this.events = events;
    }

    /** An action by a signed-in person. */
    @Transactional
    public void record(Caller actor, String action, String entityType, UUID entityId, String details) {
        events.save(new AuditEvent(actor.id(), actor.role(), action, entityType, entityId, details));
    }

    /** An automatic action (triage, reassignment). */
    @Transactional
    public void recordSystem(String action, String entityType, UUID entityId, String details) {
        events.save(new AuditEvent(null, SYSTEM, action, entityType, entityId, details));
    }

    public PageResponse<AuditEventResponse> list(String entityType, UUID entityId, Pageable pageable) {
        Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<AuditEvent> page;
        if (entityType != null && entityId != null) {
            page = events.findByEntityTypeAndEntityId(entityType, entityId, newestFirst);
        } else if (entityType != null) {
            page = events.findByEntityType(entityType, newestFirst);
        } else {
            page = events.findAll(newestFirst);
        }
        return PageResponse.of(page, AuditEventResponse::from);
    }
}
