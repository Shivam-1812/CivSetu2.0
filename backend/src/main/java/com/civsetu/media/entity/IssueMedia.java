package com.civsetu.media.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.civsetu.common.persistence.BaseEntity;

/**
 * Metadata of one stored file attached to an issue.
 */
@Entity
@Table(name = "issue_media")
public class IssueMedia extends BaseEntity {

    @Column(name = "issue_id", nullable = false, updatable = false)
    private UUID issueId;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private UUID uploadedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private MediaKind kind;

    @Column(name = "storage_key", nullable = false, length = 200, updatable = false)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 100, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    protected IssueMedia() {
    }

    public IssueMedia(UUID issueId, UUID uploadedBy, MediaKind kind, String storageKey, String contentType,
            long sizeBytes) {
        this.issueId = issueId;
        this.uploadedBy = uploadedBy;
        this.kind = kind;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
    }

    public UUID getIssueId() {
        return issueId;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public MediaKind getKind() {
        return kind;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }
}
