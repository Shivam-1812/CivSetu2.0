package com.civsetu.issue.service;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.civsetu.issue.entity.IssueStatus;
import com.civsetu.issue.repository.IssueRepository;
import com.civsetu.user.service.UserService;

/**
 * Picks who handles an issue: the active Authority of the department with the fewest open
 * issues (ties go to the same person every time, by id). Nobody chooses by hand.
 */
@Component
class AuthorityAssigner {

    static final Set<IssueStatus> OPEN = EnumSet.of(IssueStatus.ASSIGNED, IssueStatus.IN_PROGRESS);

    private final UserService users;
    private final IssueRepository issues;

    AuthorityAssigner(UserService users, IssueRepository issues) {
        this.users = users;
        this.issues = issues;
    }

    /** The least-loaded active Authority, or null if the department has none yet. */
    UUID pick(UUID departmentId) {
        List<UUID> candidates = users.activeAuthorityIds(departmentId);
        if (candidates.isEmpty()) {
            return null;
        }
        Map<UUID, Long> openIssues = new HashMap<>();
        for (Object[] row : issues.countOpenByAuthority(candidates, OPEN)) {
            openIssues.put((UUID) row[0], (Long) row[1]);
        }
        return candidates.stream()
                .min(Comparator.comparingLong((UUID id) -> openIssues.getOrDefault(id, 0L)))
                .orElseThrow();
    }

    boolean isActiveAuthorityOf(UUID authorityId, UUID departmentId) {
        return authorityId != null && users.activeAuthorityIds(departmentId).contains(authorityId);
    }
}
