package com.civsetu.audit.controller;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.civsetu.audit.dto.AuditEventResponse;
import com.civsetu.audit.service.AuditService;
import com.civsetu.common.web.PageResponse;

/**
 * Read-only audit log for Admins. Nobody can edit or delete entries through the API.
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
class AdminAuditController {

    private final AuditService audit;

    AdminAuditController(AuditService audit) {
        this.audit = audit;
    }

    /** Newest first; optionally only one entity type (e.g. ISSUE, USER) or one entity. */
    @GetMapping
    PageResponse<AuditEventResponse> list(@RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId, Pageable pageable) {
        return audit.list(entityType, entityId, pageable);
    }
}
