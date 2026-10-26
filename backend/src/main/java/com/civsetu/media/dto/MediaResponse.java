package com.civsetu.media.dto;

import java.util.UUID;

import com.civsetu.media.entity.IssueMedia;
import com.civsetu.media.entity.MediaKind;

/**
 * A stored file. {@code url} downloads it through the API (access-checked). {@code updateId} links
 * a workflow file (plan, proof) to its history entry; it is null for files attached to the report.
 */
public record MediaResponse(UUID id, MediaKind kind, UUID updateId, String contentType, long sizeBytes,
        String url) {

    public static MediaResponse from(IssueMedia media) {
        return new MediaResponse(media.getId(), media.getKind(), media.getUpdateId(), media.getContentType(), media.getSizeBytes(),
                "/api/v1/media/" + media.getId());
    }
}
