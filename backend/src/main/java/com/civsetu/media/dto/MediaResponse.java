package com.civsetu.media.dto;

import java.util.UUID;

import com.civsetu.media.entity.IssueMedia;
import com.civsetu.media.entity.MediaKind;

/**
 * A stored file. {@code url} downloads it through the API (access-checked).
 */
public record MediaResponse(UUID id, MediaKind kind, String contentType, long sizeBytes, String url) {

    public static MediaResponse from(IssueMedia media) {
        return new MediaResponse(media.getId(), media.getKind(), media.getContentType(), media.getSizeBytes(),
                "/api/v1/media/" + media.getId());
    }
}
