package com.civsetu.issue.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.civsetu.audit.service.AuditService;
import com.civsetu.department.service.DepartmentService;
import com.civsetu.issue.entity.Issue;
import com.civsetu.issue.entity.IssueAssignment;
import com.civsetu.issue.entity.IssueStatus;
import com.civsetu.issue.entity.IssueUpdate;
import com.civsetu.issue.entity.PrioritySource;
import com.civsetu.issue.repository.IssueAssignmentRepository;
import com.civsetu.issue.repository.IssueRepository;
import com.civsetu.media.dto.MediaBytes;
import com.civsetu.media.service.MediaService;
import com.civsetu.sla.service.SlaService;
import com.civsetu.triage.dto.TriageDecisionResponse;
import com.civsetu.triage.dto.TriageInput;
import com.civsetu.triage.service.TriageService;

/**
 * Routes a REPORTED issue: asks triage for department and priority, sets the deadline, picks the
 * Authority, and records it all in the history. Runs in three steps so the (possibly slow) AI call
 * holds no database lock: read a snapshot, decide, then apply under a row lock only if the issue
 * has not changed meanwhile. Safe to call more than once for the same issue.
 */
@Service
class IssueTriageService {

    private static final Logger log = LoggerFactory.getLogger(IssueTriageService.class);

    private final IssueRepository issues;
    private final IssueAssignmentRepository assignments;
    private final TriageService triage;
    private final MediaService media;
    private final SlaService sla;
    private final DepartmentService departments;
    private final AuthorityAssigner assigner;
    private final IssueHistory history;
    private final AuditService audit;
    private final TransactionTemplate readOnly;
    private final TransactionTemplate readWrite;

    IssueTriageService(IssueRepository issues, IssueAssignmentRepository assignments, TriageService triage,
            MediaService media, SlaService sla, DepartmentService departments, AuthorityAssigner assigner,
            IssueHistory history, AuditService audit, PlatformTransactionManager transactions) {
        this.issues = issues;
        this.assignments = assignments;
        this.triage = triage;
        this.media = media;
        this.sla = sla;
        this.departments = departments;
        this.assigner = assigner;
        this.history = history;
        this.audit = audit;
        // REQUIRES_NEW: triage may run right after another transaction's commit (in its after-commit
        // callback), where joining the finished transaction would silently lose these writes
        this.readOnly = new TransactionTemplate(transactions);
        this.readOnly.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readOnly.setReadOnly(true);
        this.readWrite = new TransactionTemplate(transactions);
        this.readWrite.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private record Snapshot(TriageInput input, long version) {
    }

    void triage(UUID issueId) {
        Snapshot snapshot = readOnly.execute(status -> snapshot(issueId));
        if (snapshot == null) {
            return;
        }
        TriageDecisionResponse decision = triage.decide(snapshot.input());
        readWrite.executeWithoutResult(status -> apply(issueId, snapshot, decision));
    }

    private Snapshot snapshot(UUID issueId) {
        Issue issue = issues.findById(issueId).orElse(null);
        if (issue == null || issue.getStatus() != IssueStatus.REPORTED) {
            return null;
        }
        Set<UUID> tried = assignments.findByIssueIdOrderByCreatedAtAsc(issueId).stream()
                .map(IssueAssignment::getDepartmentId)
                .collect(Collectors.toSet());
        List<MediaBytes> images = media.readReportImages(issueId, triage.imagesWanted());
        return new Snapshot(new TriageInput(issueId, issue.getTitle(), issue.getDescription(), issue.getCategoryId(),
                issue.getAddress(), images, tried), issue.getVersion());
    }

    private void apply(UUID issueId, Snapshot snapshot, TriageDecisionResponse decision) {
        Issue issue = issues.findByIdForUpdate(issueId).orElse(null);
        if (issue == null || issue.getStatus() != IssueStatus.REPORTED || issue.getVersion() != snapshot.version()) {
            log.info("Triage result for issue {} discarded: the issue changed meanwhile", issueId);
            return;
        }
        boolean reroute = !snapshot.input().excludedDepartments().isEmpty();
        // The clock starts at the report; a re-routed issue keeps its original deadline
        Instant deadline = reroute ? issue.getSlaDueAt() : sla.dueAt(decision.priority(), issue.getCreatedAt());
        UUID authority = assigner.pick(decision.departmentId());

        issue.route(decision.categoryId(), decision.departmentId(), decision.priority(),
                PrioritySource.valueOf(decision.source().name()), decision.flaggedForReview(), deadline, authority);
        assignments.save(new IssueAssignment(issueId, decision.departmentId(), authority,
                reroute ? IssueAssignment.Reason.RE_ROUTE : IssueAssignment.Reason.TRIAGE));

        String department = departments.lookup().get(decision.departmentId()).name();
        String remarks = "Routed to " + department + " with " + decision.priority().name().toLowerCase()
                + " priority (" + (decision.source().name().equals("AI") ? "AI" : "rules") + "). "
                + decision.reason()
                + (authority == null ? " Waiting for an Authority to be available in this department." : "");
        history.record(issue, null, IssueUpdate.ActorRole.SYSTEM, IssueUpdate.Action.ASSIGNED, IssueStatus.REPORTED,
                remarks);
        audit.recordSystem(reroute ? "ISSUE_REROUTED" : "ISSUE_ROUTED", "ISSUE", issueId,
                "department=" + decision.departmentId() + ", priority=" + decision.priority() + ", source="
                        + decision.source() + ", authority=" + authority + ", decision=" + decision.id());
    }
}
