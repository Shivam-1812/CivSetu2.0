package com.civsetu.issue.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.civsetu.audit.service.AuditService;
import com.civsetu.common.error.ApiException;
import com.civsetu.common.security.Caller;
import com.civsetu.department.dto.DepartmentResponse;
import com.civsetu.department.service.DepartmentService;
import com.civsetu.issue.config.WorkflowProperties;
import com.civsetu.issue.dto.DuplicateRequest;
import com.civsetu.issue.dto.HistoryEntryResponse;
import com.civsetu.issue.dto.IssueDetailResponse;
import com.civsetu.issue.dto.PlanRequest;
import com.civsetu.issue.dto.RejectRequest;
import com.civsetu.issue.dto.ReopenRequest;
import com.civsetu.issue.dto.ResolveRequest;
import com.civsetu.issue.dto.ReturnRequest;
import com.civsetu.issue.entity.Issue;
import com.civsetu.issue.entity.IssueAssignment;
import com.civsetu.issue.entity.IssueStatus;
import com.civsetu.issue.entity.IssueUpdate;
import com.civsetu.issue.repository.IssueAssignmentRepository;
import com.civsetu.issue.repository.IssueRepository;
import com.civsetu.issue.repository.IssueUpdateRepository;
import com.civsetu.media.dto.MediaResponse;
import com.civsetu.media.entity.MediaKind;
import com.civsetu.media.service.MediaService;
import com.civsetu.sla.service.SlaService;

/**
 * What people can do with an issue after the system has routed it. Every action is checked
 * against {@link TransitionPolicy}, written to the public history and to the audit log.
 * Each request carries the issue version the person saw, so acting on a stale view fails with 409.
 */
@Service
@Transactional
public class IssueWorkflowService {

    private final IssueRepository issues;
    private final IssueAssignmentRepository assignments;
    private final IssueUpdateRepository updates;
    private final IssueHistory history;
    private final AuthorityAssigner assigner;
    private final MediaService media;
    private final SlaService sla;
    private final DepartmentService departments;
    private final AuditService audit;
    private final IssueResponseMapper mapper;
    private final ApplicationEventPublisher events;
    private final WorkflowProperties properties;
    private final Clock clock;

    IssueWorkflowService(IssueRepository issues, IssueAssignmentRepository assignments, IssueUpdateRepository updates,
            IssueHistory history, AuthorityAssigner assigner, MediaService media, SlaService sla,
            DepartmentService departments, AuditService audit, IssueResponseMapper mapper,
            ApplicationEventPublisher events, WorkflowProperties properties, Clock clock) {
        this.issues = issues;
        this.assignments = assignments;
        this.updates = updates;
        this.history = history;
        this.assigner = assigner;
        this.media = media;
        this.sla = sla;
        this.departments = departments;
        this.audit = audit;
        this.mapper = mapper;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    /** The assigned Authority's work plan; ASSIGNED → IN_PROGRESS. Plan documents are optional. */
    public IssueDetailResponse plan(UUID issueId, Caller caller, PlanRequest request, List<MultipartFile> documents) {
        Issue issue = load(issueId, request.version());
        TransitionPolicy.check(TransitionPolicy.Action.PLAN, issue, caller);
        IssueStatus from = issue.getStatus();
        issue.startWork();
        IssueUpdate step = history.record(new IssueUpdate(issueId, caller.id(), IssueUpdate.ActorRole.AUTHORITY,
                IssueUpdate.Action.PLANNED, from, issue.getStatus(), issue.getDepartmentId(), trim(request.remarks()),
                request.actionTaken().trim(), trim(request.staffAllocated()), trim(request.resourcesUsed())), issue);
        media.attach(issueId, step.getId(), caller.id(), MediaKind.PLAN_DOC, documents);
        audit.record(caller, "ISSUE_PLANNED", "ISSUE", issueId, null);
        return respond(issue, caller);
    }

    /** IN_PROGRESS → RESOLVED. Remarks and at least one proof photo are required. */
    public IssueDetailResponse resolve(UUID issueId, Caller caller, ResolveRequest request, List<MultipartFile> proofs) {
        Issue issue = load(issueId, request.version());
        TransitionPolicy.check(TransitionPolicy.Action.RESOLVE, issue, caller);
        if (proofs == null || proofs.stream().allMatch(file -> file == null || file.isEmpty())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROOF_REQUIRED",
                    "Attach at least one photo showing the issue is fixed.");
        }
        IssueStatus from = issue.getStatus();
        Instant now = clock.instant();
        issue.resolve(now);
        endCurrentAssignment(issueId, now);
        IssueUpdate step = history.record(issue, caller.id(), IssueUpdate.ActorRole.AUTHORITY,
                IssueUpdate.Action.RESOLVED, from, request.remarks().trim()
                        + (now.isAfter(issue.getSlaDueAt()) ? " (resolved after the deadline)" : ""));
        media.attach(issueId, step.getId(), caller.id(), MediaKind.RESOLUTION_PROOF, proofs);
        audit.record(caller, "ISSUE_RESOLVED", "ISSUE", issueId, "withinDeadline=" + !now.isAfter(issue.getSlaDueAt()));
        return respond(issue, caller);
    }

    /** ASSIGNED or IN_PROGRESS → REJECTED, with a reason. Evidence photos are optional. */
    public IssueDetailResponse reject(UUID issueId, Caller caller, RejectRequest request, List<MultipartFile> evidence) {
        Issue issue = load(issueId, request.version());
        TransitionPolicy.check(TransitionPolicy.Action.REJECT, issue, caller);
        IssueStatus from = issue.getStatus();
        Instant now = clock.instant();
        issue.reject(now);
        endCurrentAssignment(issueId, now);
        IssueUpdate step = history.record(issue, caller.id(), IssueUpdate.ActorRole.AUTHORITY,
                IssueUpdate.Action.REJECTED, from, request.reason().trim());
        media.attach(issueId, step.getId(), caller.id(), MediaKind.REJECTION_PROOF, evidence);
        audit.record(caller, "ISSUE_REJECTED", "ISSUE", issueId, null);
        return respond(issue, caller);
    }

    /** Not this department's issue: back to REPORTED, and the system re-routes it after commit. */
    public IssueDetailResponse returnForRerouting(UUID issueId, Caller caller, ReturnRequest request) {
        Issue issue = load(issueId, request.version());
        TransitionPolicy.check(TransitionPolicy.Action.RETURN, issue, caller);
        IssueStatus from = issue.getStatus();
        issue.returnForRerouting();
        endCurrentAssignment(issueId, clock.instant());
        history.record(issue, caller.id(), IssueUpdate.ActorRole.AUTHORITY, IssueUpdate.Action.RETURNED, from,
                "Returned as not this department's responsibility: " + request.reason().trim());
        audit.record(caller, "ISSUE_RETURNED", "ISSUE", issueId, "department=" + issue.getDepartmentId());
        events.publishEvent(new IssueReportedEvent(issueId));
        return respond(issue, caller);
    }

    /** ASSIGNED or IN_PROGRESS → DUPLICATE, pointing at the issue that already covers the problem. */
    public IssueDetailResponse markDuplicate(UUID issueId, Caller caller, DuplicateRequest request) {
        Issue issue = load(issueId, request.version());
        TransitionPolicy.check(TransitionPolicy.Action.MARK_DUPLICATE, issue, caller);
        Issue original = issues.findById(request.duplicateOfId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "ORIGINAL_NOT_FOUND",
                        "The original issue was not found."));
        if (original.getId().equals(issueId) || original.getStatus() == IssueStatus.DUPLICATE
                || original.getStatus() == IssueStatus.REJECTED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ORIGINAL",
                    "The original must be another issue that is open or resolved.");
        }
        IssueStatus from = issue.getStatus();
        Instant now = clock.instant();
        issue.markDuplicate(original.getId(), now);
        endCurrentAssignment(issueId, now);
        history.record(issue, caller.id(), IssueUpdate.ActorRole.AUTHORITY, IssueUpdate.Action.MARKED_DUPLICATE,
                from, "Already reported as \"" + original.getTitle() + "\" (" + original.getId() + ")."
                        + (request.remarks() == null || request.remarks().isBlank() ? "" : " " + request.remarks().trim()));
        audit.record(caller, "ISSUE_MARKED_DUPLICATE", "ISSUE", issueId, "original=" + original.getId());
        return respond(issue, caller);
    }

    /**
     * The reporter reopens a resolved, rejected or duplicate issue, within the reopen window and limit.
     * It goes back to the same Authority if still active, otherwise to the least-loaded one,
     * with a fresh deadline for its priority.
     */
    public IssueDetailResponse reopen(UUID issueId, Caller caller, ReopenRequest request) {
        Issue issue = load(issueId, request.version());
        TransitionPolicy.check(TransitionPolicy.Action.REOPEN, issue, caller);
        Instant now = clock.instant();
        if (issue.getReopenCount() >= properties.maxReopens()) {
            throw new ApiException(HttpStatus.CONFLICT, "REOPEN_LIMIT_REACHED",
                    "This issue has already been reopened " + issue.getReopenCount() + " time(s).");
        }
        if (now.isAfter(issue.getClosedAt().plus(properties.reopenWindow()))) {
            throw new ApiException(HttpStatus.CONFLICT, "REOPEN_WINDOW_CLOSED",
                    "Issues can be reopened up to " + properties.reopenWindow().toDays()
                            + " days after they are closed. Please report it again.");
        }
        IssueStatus from = issue.getStatus();
        UUID authority = assigner.isActiveAuthorityOf(issue.getAssignedAuthorityId(), issue.getDepartmentId())
                ? issue.getAssignedAuthorityId()
                : assigner.pick(issue.getDepartmentId());
        Instant deadline = sla.dueAt(issue.getPriority(), now);
        issue.reopen(deadline, authority);
        assignments.save(new IssueAssignment(issueId, issue.getDepartmentId(), authority,
                IssueAssignment.Reason.REOPEN));
        history.record(issue, caller.id(), IssueUpdate.ActorRole.CITIZEN, IssueUpdate.Action.REOPENED, from,
                request.reason().trim() + " (new deadline " + deadline + ")");
        audit.record(caller, "ISSUE_REOPENED", "ISSUE", issueId, "authority=" + authority);
        return respond(issue, caller);
    }

    /** The public timeline, oldest first. Attached files are included only if the caller may see them. */
    @Transactional(readOnly = true)
    public List<HistoryEntryResponse> history(UUID issueId, Caller caller) {
        Issue issue = issues.findById(issueId).orElseThrow(() -> ApiException.notFound("Issue"));
        Map<UUID, DepartmentResponse> departmentNames = departments.lookup();
        List<MediaResponse> files = media.listForIssue(issueId).stream()
                .filter(file -> file.updateId() != null)
                .filter(file -> IssueMediaAccess.canView(issue, file.kind(), caller))
                .toList();
        return updates.findByIssueIdOrderBySeqAsc(issueId).stream()
                .map(step -> new HistoryEntryResponse(step.getId(), step.getAction(), step.getFromStatus(),
                        step.getToStatus(), step.getActorRole(),
                        step.getDepartmentId() == null ? null : departmentNames.get(step.getDepartmentId()).name(),
                        step.getRemarks(), step.getActionTaken(), step.getStaffAllocated(), step.getResourcesUsed(),
                        step.getCreatedAt(),
                        files.stream().filter(file -> step.getId().equals(file.updateId())).toList()))
                .toList();
    }

    /** Locks the issue and checks the caller acted on the current version. */
    private Issue load(UUID issueId, long expectedVersion) {
        Issue issue = issues.findByIdForUpdate(issueId).orElseThrow(() -> ApiException.notFound("Issue"));
        if (issue.getVersion() != expectedVersion) {
            throw new ApiException(HttpStatus.CONFLICT, "STALE_ISSUE",
                    "This issue changed since you loaded it. Reload and try again.");
        }
        return issue;
    }

    private void endCurrentAssignment(UUID issueId, Instant at) {
        assignments.findFirstByIssueIdAndEndedAtIsNullOrderByCreatedAtDesc(issueId).ifPresent(a -> a.end(at));
    }

    private IssueDetailResponse respond(Issue issue, Caller caller) {
        // Flush so the response carries the new version number for the next action
        issues.flush();
        return mapper.detail(issue, caller);
    }

    private static String trim(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
