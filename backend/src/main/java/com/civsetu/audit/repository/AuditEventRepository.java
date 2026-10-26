package com.civsetu.audit.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.civsetu.audit.entity.AuditEvent;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    Page<AuditEvent> findByEntityType(String entityType, Pageable pageable);

    Page<AuditEvent> findByEntityTypeAndEntityId(String entityType, UUID entityId, Pageable pageable);
}
