package com.civsetu.issue.service;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.civsetu.audit.service.AuditService;
import com.civsetu.issue.config.WorkflowProperties;
import com.civsetu.issue.entity.Issue;
import com.civsetu.issue.entity.IssueAssignment;
import com.civsetu.issue.entity.IssueStatus;
import com.civsetu.issue.entity.IssueUpdate;
import com.civsetu.issue.repository.IssueAssignmentRepository;
import com.civsetu.issue.repository.IssueRepository;
import com.civsetu.user.dto.UserResponse;
import com.civsetu.user.service.UserService;

/**
 * The workflow's safety net, run on a schedule:
 * <ol>
 *   <li>triages issues still REPORTED after the retry delay (lost after a restart, queue full, AI down);</li>
 *   <li>assigns an Authority to open issues whose department had none when they were routed;</li>
 *   <li>moves open issues away from Authorities whose accounts were disabled.</li>
 * </ol>
 * Every change is automatic, recorded in the history, and audited. Each issue is handled in its own
 * transaction, so one failure does not stop the rest.
 */
@Component
class WorkflowSweeper {

    private static final Logger log = LoggerFactory.getLogger(WorkflowSweeper.class);

    private final IssueRepository issues;
    private final IssueAssignmentRepository assignments;
    private final IssueTriageService triage;
    private final AuthorityAssigner assigner;
    private final IssueHistory history;
    private final UserService users;
    private final AuditService audit;
    private final WorkflowProperties properties;
    private final Clock clock;
    private final TransactionTemplate transaction;

    WorkflowSweeper(IssueRepository issues, IssueAssignmentRepository assignments, IssueTriageService triage,
            AuthorityAssigner assigner, IssueHistory history, UserService users, AuditService audit,
            WorkflowProperties properties, Clock clock, PlatformTransactionManager transactions) {
        this.issues = issues;
        this.assignments = assignments;
        this.triage = triage;
        this.assigner = assigner;
        this.history = history;
        this.users = users;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactions);
    }

    @Scheduled(fixedDelayString = "${civsetu.workflow.sweep-interval}",
            initialDelayString = "${civsetu.workflow.sweep-interval}")
    void sweep() {
        int triaged = triageStuckIssues();
        int assigned = assignWaitingIssues();
        int moved = moveIssuesFromDisabledAuthorities();
        if (triaged + assigned + moved > 0) {
            log.info("Workflow sweep: triaged {}, assigned {}, moved from disabled Authorities {}",
                    triaged, assigned, moved);
        }
    }

    int triageStuckIssues() {
        List<Issue> stuck = issues.findByStatusAndUpdatedAtBeforeOrderByCreatedAtAsc(IssueStatus.REPORTED,
                clock.instant().minus(properties.retryTriageAfter()), Limit.of(properties.sweepBatchSize()));
        int done = 0;
        for (Issue issue : stuck) {
            try {
                triage.triage(issue.getId());
                done++;
            } catch (RuntimeException e) {
                log.error("Sweep could not triage issue {}", issue.getId(), e);
            }
        }
        return done;
    }

    /** Only departments that now have an active Authority are visited, so departments still without one
     *  can never crowd the others out of the batch. */
    int assignWaitingIssues() {
        int assigned = 0;
        for (UUID department : issues.findDepartmentsWithWaitingIssues(AuthorityAssigner.OPEN)) {
            if (users.activeAuthorityIds(department).isEmpty()) {
                continue;
            }
            for (Issue issue : issues.findByDepartmentIdAndStatusInAndAssignedAuthorityIdIsNullOrderByCreatedAtAsc(
                    department, AuthorityAssigner.OPEN, Limit.of(properties.sweepBatchSize()))) {
                if (reassign(issue.getId(), null)) {
                    assigned++;
                }
            }
        }
        return assigned;
    }

    int moveIssuesFromDisabledAuthorities() {
        int moved = 0;
        for (UUID authority : issues.findOpenAuthorityIds(AuthorityAssigner.OPEN)) {
            boolean active = users.findById(authority).map(UserResponse::active).orElse(false);
            if (!active) {
                for (Issue issue : issues.findByAssignedAuthorityIdAndStatusIn(authority, AuthorityAssigner.OPEN)) {
                    if (reassign(issue.getId(), authority)) {
                        moved++;
                    }
                }
            }
        }
        return moved;
    }

    /** Hands an open issue to the least-loaded active Authority; false if none is available yet. */
    private boolean reassign(UUID issueId, UUID previousAuthority) {
        Boolean changed = transaction.execute(status -> {
            Issue issue = issues.findByIdForUpdate(issueId).orElse(null);
            if (issue == null || !AuthorityAssigner.OPEN.contains(issue.getStatus())
                    || !Objects.equals(issue.getAssignedAuthorityId(), previousAuthority)) {
                return false;
            }
            UUID next = assigner.pick(issue.getDepartmentId());
            if (next == null) {
                if (previousAuthority != null) {
                    // The disabled Authority must not keep the issue even if nobody can take it yet
                    issue.reassign(null);
                    recordHandOver(issue, null, "The assigned Authority is no longer active. "
                            + "Waiting for an Authority to be available in this department.");
                    return true;
                }
                return false;
            }
            issue.reassign(next);
            recordHandOver(issue, next, previousAuthority == null
                    ? "Assigned to an Authority of the department."
                    : "The assigned Authority is no longer active; handed over to another Authority.");
            return true;
        });
        return Boolean.TRUE.equals(changed);
    }

    private void recordHandOver(Issue issue, UUID authority, String remarks) {
        assignments.findFirstByIssueIdAndEndedAtIsNullOrderByCreatedAtDesc(issue.getId())
                .ifPresent(current -> current.end(clock.instant()));
        assignments.save(new IssueAssignment(issue.getId(), issue.getDepartmentId(), authority,
                IssueAssignment.Reason.REBALANCE));
        history.record(issue, null, IssueUpdate.ActorRole.SYSTEM, IssueUpdate.Action.REASSIGNED, issue.getStatus(),
                remarks);
        audit.recordSystem("ISSUE_REASSIGNED", "ISSUE", issue.getId(), "authority=" + authority);
    }
}
