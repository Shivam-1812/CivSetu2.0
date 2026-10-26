package com.civsetu.media.service;

import java.util.UUID;

import com.civsetu.common.security.Caller;
import com.civsetu.media.entity.MediaKind;

/**
 * Decides who may see a file attached to an issue. Implemented by the issue feature, which
 * knows the reporter and department, so the media feature does not depend on issues.
 */
public interface MediaAccessPolicy {

    boolean canView(UUID issueId, MediaKind kind, Caller caller);
}
