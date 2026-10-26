package com.civsetu.issue.service;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.civsetu.common.security.Caller;
import com.civsetu.issue.entity.Issue;
import com.civsetu.issue.repository.IssueRepository;
import com.civsetu.media.entity.MediaKind;
import com.civsetu.media.service.MediaAccessPolicy;

/**
 * Who may see an issue's files. Report photos and resolution proof are public (transparency);
 * voice notes, work plans and rejection evidence are visible to the reporter, Admins and
 * Authorities of the issue's department only.
 */
@Component
class IssueMediaAccess implements MediaAccessPolicy {

    private static final Set<MediaKind> PUBLIC_KINDS = EnumSet.of(MediaKind.IMAGE, MediaKind.RESOLUTION_PROOF);

    private final IssueRepository issues;

    IssueMediaAccess(IssueRepository issues) {
        this.issues = issues;
    }

    @Override
    public boolean canView(UUID issueId, MediaKind kind, Caller caller) {
        return issues.findById(issueId).map(issue -> canView(issue, kind, caller)).orElse(false);
    }

    static boolean canView(Issue issue, MediaKind kind, Caller caller) {
        return PUBLIC_KINDS.contains(kind)
                || caller.is(issue.getReporterId())
                || caller.isAdmin()
                || caller.isAuthorityOf(issue.getDepartmentId());
    }
}
