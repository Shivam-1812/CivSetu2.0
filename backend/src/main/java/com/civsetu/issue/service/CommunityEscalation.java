package com.civsetu.issue.service;

import java.time.Instant;
import java.util.Locale;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.civsetu.audit.service.AuditService;
import com.civsetu.common.domain.Priority;
import com.civsetu.engagement.dto.VoteTallyChangedEvent;
import com.civsetu.engagement.service.EngagementService;
import com.civsetu.issue.entity.Issue;
import com.civsetu.issue.entity.IssueUpdate;
import com.civsetu.issue.repository.IssueRepository;
import com.civsetu.sla.service.SlaService;

/**
 * When enough residents confirm an open issue (net "agree" votes reach the threshold), the system
 * raises its priority one level, once. The deadline moves earlier if the higher priority demands it,
 * never later. Runs inside the vote's transaction, and is recorded in the public history.
 */
@Component
class CommunityEscalation {

    private final IssueRepository issues;
    private final EngagementService engagement;
    private final SlaService sla;
    private final IssueHistory history;
    private final AuditService audit;

    CommunityEscalation(IssueRepository issues, EngagementService engagement, SlaService sla, IssueHistory history,
            AuditService audit) {
        this.issues = issues;
        this.engagement = engagement;
        this.sla = sla;
        this.history = history;
        this.audit = audit;
    }

    @EventListener
    void onVotesChanged(VoteTallyChangedEvent event) {
        if (event.net() < engagement.escalationThreshold()) {
            return;
        }
        Issue issue = issues.findByIdForUpdate(event.issueId()).orElse(null);
        if (issue == null || !AuthorityAssigner.OPEN.contains(issue.getStatus())
                || issue.isPriorityRaisedByCommunity() || issue.getPriority() == Priority.CRITICAL) {
            return;
        }
        Priority before = issue.getPriority();
        Priority raised = Priority.values()[before.ordinal() - 1];
        Instant byHigherPriority = sla.dueAt(raised, issue.getCreatedAt());
        Instant deadline = byHigherPriority.isBefore(issue.getSlaDueAt()) ? byHigherPriority : issue.getSlaDueAt();
        issue.raisePriorityByCommunity(raised, deadline);

        history.record(issue, null, IssueUpdate.ActorRole.SYSTEM, IssueUpdate.Action.PRIORITY_RAISED,
                issue.getStatus(), "Priority raised from " + label(before) + " to " + label(raised) + " after "
                        + event.agree() + " residents confirmed the problem (" + event.disagree() + " disagreed).");
        audit.recordSystem("PRIORITY_RAISED_BY_COMMUNITY", "ISSUE", issue.getId(),
                "from=" + before + ", to=" + raised + ", agree=" + event.agree() + ", disagree=" + event.disagree());
    }

    private static String label(Priority priority) {
        return priority.name().toLowerCase(Locale.ROOT);
    }
}
