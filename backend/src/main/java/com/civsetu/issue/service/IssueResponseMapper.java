package com.civsetu.issue.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.civsetu.common.security.Caller;
import com.civsetu.department.dto.CategoryResponse;
import com.civsetu.department.dto.DepartmentResponse;
import com.civsetu.department.service.CategoryService;
import com.civsetu.department.service.DepartmentService;
import com.civsetu.engagement.service.EngagementService;
import com.civsetu.issue.dto.IssueDetailResponse;
import com.civsetu.issue.dto.IssueSummaryResponse;
import com.civsetu.issue.dto.MapResponse;
import com.civsetu.issue.dto.SimilarIssueResponse;
import com.civsetu.issue.entity.Issue;
import com.civsetu.media.dto.MediaResponse;
import com.civsetu.media.service.MediaService;
import com.civsetu.sla.dto.SlaRules;
import com.civsetu.sla.dto.SlaState;
import com.civsetu.sla.service.SlaService;
import com.civsetu.triage.dto.TriageDecisionResponse;
import com.civsetu.triage.service.TriageService;
import com.civsetu.user.service.UserService;

/**
 * Builds issue responses, adding category and department names and applying the
 * field-visibility rules for the caller.
 */
@Component
class IssueResponseMapper {

    private final CategoryService categories;
    private final DepartmentService departments;
    private final UserService users;
    private final MediaService media;
    private final TriageService triage;
    private final EngagementService engagement;
    private final SlaService sla;
    private final Clock clock;

    IssueResponseMapper(CategoryService categories, DepartmentService departments, UserService users,
            MediaService media, TriageService triage, EngagementService engagement, SlaService sla, Clock clock) {
        this.categories = categories;
        this.departments = departments;
        this.users = users;
        this.media = media;
        this.triage = triage;
        this.engagement = engagement;
        this.sla = sla;
        this.clock = clock;
    }

    /** Names and SLA targets, loaded once per request (the tables are small), and one "now" for all rows. */
    record Labels(Map<UUID, CategoryResponse> categories, Map<UUID, DepartmentResponse> departments, SlaRules sla,
            Instant now) {

        SlaState slaState(Issue issue) {
            return sla.state(issue.getPriority(), issue.getSlaDueAt(), issue.getResolvedAt(), issue.isOpen(), now);
        }
    }

    Labels labels() {
        return new Labels(categories.lookup(), departments.lookup(), sla.rules(), clock.instant());
    }

    IssueSummaryResponse summary(Issue issue, Labels labels) {
        CategoryResponse category = labels.categories().get(issue.getCategoryId());
        DepartmentResponse department = labels.departments().get(issue.getDepartmentId());
        return new IssueSummaryResponse(issue.getId(), issue.getTitle(), category.code(), category.label(),
                department.id(), department.name(), issue.getStatus(), issue.getPriority(), issue.getAddress(),
                issue.getLatitude(), issue.getLongitude(), issue.getCreatedAt(), issue.getSlaDueAt(),
                labels.slaState(issue));
    }

    MapResponse.Point mapPoint(Issue issue, Labels labels) {
        return new MapResponse.Point(issue.getId(), issue.getLatitude(), issue.getLongitude(),
                labels.categories().get(issue.getCategoryId()).code(), issue.getStatus(), issue.getPriority());
    }

    IssueDetailResponse detail(Issue issue, Caller caller) {
        return detail(issue, caller, null);
    }

    IssueDetailResponse detail(Issue issue, Caller caller, List<SimilarIssueResponse> possibleDuplicates) {
        Labels labels = labels();
        CategoryResponse category = labels.categories().get(issue.getCategoryId());
        DepartmentResponse department = labels.departments().get(issue.getDepartmentId());

        boolean isReporter = caller.is(issue.getReporterId());
        boolean isStaff = caller.isAdmin() || caller.isAuthorityOf(issue.getDepartmentId());
        IssueDetailResponse.Reporter reporter = isReporter || isStaff
                ? users.findById(issue.getReporterId())
                        .map(user -> new IssueDetailResponse.Reporter(user.id(), user.name(), user.email(), user.phone()))
                        .orElse(null)
                : null;
        // Files attached to the report itself; files of workflow steps are listed in the history
        List<MediaResponse> visibleMedia = media.listForIssue(issue.getId()).stream()
                .filter(file -> file.updateId() == null)
                .filter(file -> IssueMediaAccess.canView(issue, file.kind(), caller))
                .toList();
        String priorityReason = triage.latest(issue.getId()).map(TriageDecisionResponse::reason).orElse(null);

        return new IssueDetailResponse(issue.getId(), issue.getTitle(), issue.getDescription(), category.code(),
                category.label(), department.id(), department.name(), issue.getStatus(), issue.getPriority(),
                issue.getPrioritySource(), priorityReason, issue.getAddress(), issue.getLatitude(),
                issue.getLongitude(), issue.getCreatedAt(), issue.getUpdatedAt(), issue.getSlaDueAt(),
                labels.slaState(issue), issue.getResolvedAt(), issue.getClosedAt(), issue.getReopenCount(),
                issue.getDuplicateOfId(),
                issue.getVersion(), isReporter, reporter,
                isStaff ? issue.getAssignedAuthorityId() : null,
                isStaff ? issue.isFlaggedForReview() : null,
                visibleMedia, engagement.tally(issue.getId(), caller), engagement.commentCount(issue.getId()),
                possibleDuplicates);
    }
}
