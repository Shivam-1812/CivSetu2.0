package com.civsetu.engagement.service;

import java.util.Optional;
import java.util.UUID;

import com.civsetu.engagement.dto.IssueRef;

/**
 * Finds the issue being voted or commented on. Implemented by the issue feature, so engagement
 * does not depend on issues.
 */
public interface IssueLookup {

    Optional<IssueRef> find(UUID issueId);
}
