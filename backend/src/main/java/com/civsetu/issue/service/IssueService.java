package com.civsetu.issue.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.civsetu.common.domain.Priority;
import com.civsetu.common.error.ApiException;
import com.civsetu.common.geo.GeoPoints;
import com.civsetu.common.security.Caller;
import com.civsetu.common.web.PageResponse;
import com.civsetu.department.dto.CategoryResponse;
import com.civsetu.department.service.CategoryService;
import com.civsetu.issue.dto.BoundingBox;
import com.civsetu.issue.dto.CreateIssueRequest;
import com.civsetu.issue.dto.IssueCluster;
import com.civsetu.issue.dto.IssueDetailResponse;
import com.civsetu.issue.dto.IssueSearchParams;
import com.civsetu.issue.dto.IssueSummaryResponse;
import com.civsetu.issue.dto.MapResponse;
import com.civsetu.issue.dto.SimilarIssueParams;
import com.civsetu.issue.dto.SimilarIssueResponse;
import com.civsetu.issue.config.DuplicateProperties;
import com.civsetu.issue.entity.Issue;
import com.civsetu.issue.entity.IssueStatus;
import com.civsetu.issue.entity.IssueUpdate;
import com.civsetu.issue.entity.PrioritySource;
import com.civsetu.issue.repository.IssueFilter;
import com.civsetu.issue.repository.IssueRepository;
import com.civsetu.issue.repository.IssueSpecifications;
import com.civsetu.media.entity.MediaKind;
import com.civsetu.media.service.MediaService;
import com.civsetu.sla.service.SlaService;

/**
 * Reporting and finding issues. Status changes (assign, resolve, ...) arrive in Phase 5.
 */
@Service
@Transactional(readOnly = true)
public class IssueService {

    /** Most markers returned for one map viewport. */
    static final int MAP_LIMIT = 1000;
    // Matches no row; stands in for "none" where a query needs a non-null id
    private static final UUID NO_ISSUE = new UUID(0, 0);
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final IssueRepository issues;
    private final CategoryService categories;
    private final SlaService sla;
    private final MediaService media;
    private final IssueResponseMapper mapper;
    private final IssueHistory history;
    private final ApplicationEventPublisher events;
    private final DuplicateProperties duplicates;
    private final Clock clock;

    IssueService(IssueRepository issues, CategoryService categories, SlaService sla, MediaService media,
            IssueResponseMapper mapper, IssueHistory history, ApplicationEventPublisher events,
            DuplicateProperties duplicates, Clock clock) {
        this.issues = issues;
        this.categories = categories;
        this.sla = sla;
        this.media = media;
        this.mapper = mapper;
        this.history = history;
        this.events = events;
        this.duplicates = duplicates;
        this.clock = clock;
    }

    /** A Citizen's new report without attachments. */
    @Transactional
    public IssueDetailResponse report(Caller reporter, CreateIssueRequest request) {
        return report(reporter, request, List.of(), null);
    }

    /**
     * A Citizen's new report, saved as REPORTED. Department, priority and deadline start as the
     * category's defaults; automatic triage replaces them right after the report is committed.
     * Photos and the voice note are stored in the same transaction: if any file is rejected,
     * nothing is saved.
     */
    @Transactional
    public IssueDetailResponse report(Caller reporter, CreateIssueRequest request, List<MultipartFile> images,
            MultipartFile voice) {
        CategoryResponse route = categories.routeNewIssue(request.category());
        Priority priority = route.defaultPriority();
        Issue issue = issues.save(new Issue(reporter.id(), request.title().trim(), request.description().trim(),
                route.id(), route.departmentId(), priority, PrioritySource.RULE,
                GeoPoints.of(request.latitude(), request.longitude()), request.address().trim(),
                sla.dueAt(priority, clock.instant())));
        media.attach(issue.getId(), reporter.id(), MediaKind.IMAGE, images);
        media.attach(issue.getId(), reporter.id(), MediaKind.VOICE, voice == null ? List.of() : List.of(voice));
        history.record(issue, reporter.id(), IssueUpdate.ActorRole.CITIZEN, IssueUpdate.Action.REPORTED, null,
                "Issue reported.");
        events.publishEvent(new IssueReportedEvent(issue.getId()));
        // Shown to the reporter right away: someone may already have reported the same problem
        return mapper.detail(issue, reporter, similar(issue.getLatitude(), issue.getLongitude(), issue.getCategoryId(),
                issue.getTitle(), issue.getId()));
    }

    /**
     * Areas with several open issues, for surveys.
     *
     * @param departmentId only this department's issues, or null for all
     */
    public List<IssueCluster> clusters(int radiusMeters, int minIssues, UUID departmentId) {
        return issues.findClusters(radiusMeters, minIssues, departmentId == null, orNone(departmentId)).stream()
                .map(row -> new IssueCluster(((Number) row[0]).doubleValue(), ((Number) row[1]).doubleValue(),
                        ((Number) row[2]).intValue(), ((Number) row[3]).intValue()))
                .toList();
    }

    /**
     * People who reported an open issue near a point: the audience of an area survey.
     *
     * @param departmentId only this department's issues, or null for all
     */
    public List<UUID> reportersNear(double latitude, double longitude, int radiusMeters, UUID departmentId) {
        return issues.findReportersNear(latitude, longitude, radiusMeters, departmentId == null,
                orNone(departmentId));
    }

    private static UUID orNone(UUID id) {
        return id == null ? NO_ISSUE : id;
    }

    /** Open issues that may describe the same problem as this one. */
    public List<SimilarIssueResponse> similarTo(UUID issueId) {
        Issue issue = issues.findById(issueId).orElseThrow(() -> ApiException.notFound("Issue"));
        return similar(issue.getLatitude(), issue.getLongitude(), issue.getCategoryId(), issue.getTitle(),
                issue.getId());
    }

    /** Before reporting: is this problem already reported nearby? */
    public List<SimilarIssueResponse> similarAt(SimilarIssueParams params) {
        UUID categoryId = categories.findByCode(params.category().trim())
                .map(CategoryResponse::id)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_CATEGORY",
                        "Category '" + params.category() + "' does not exist."));
        return similar(params.latitude(), params.longitude(), categoryId, params.title(), NO_ISSUE);
    }

    /** An Authority's work queue: open issues assigned to them, newest first. */
    public PageResponse<IssueSummaryResponse> assignedTo(Caller authority, List<IssueStatus> statuses,
            Pageable pageable) {
        List<IssueStatus> wanted = statuses == null || statuses.isEmpty()
                ? List.of(IssueStatus.ASSIGNED, IssueStatus.IN_PROGRESS)
                : statuses;
        IssueFilter queue = new IssueFilter(wanted, null, null, null, null, null, null, null, authority.id(),
                null, null, null, null, null);
        return page(queue, pageable, authority);
    }

    public PageResponse<IssueSummaryResponse> search(IssueSearchParams params, Pageable pageable, Caller viewer) {
        return page(toFilter(params), pageable, viewer);
    }

    /** The caller's own reports, newest first. */
    public PageResponse<IssueSummaryResponse> reportedBy(Caller caller, Pageable pageable) {
        IssueFilter mine = new IssueFilter(null, null, null, null, null, null, null, caller.id(), null,
                null, null, null, null, null);
        return page(mine, pageable, caller);
    }

    /** Markers for a map viewport; the bounding box is required. */
    public MapResponse map(IssueSearchParams params) {
        if (params.bbox() == null || params.bbox().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BBOX_REQUIRED",
                    "The map needs a bbox=minLng,minLat,maxLng,maxLat parameter.");
        }
        List<Issue> found = issues.findBy(IssueSpecifications.matching(toFilter(params)),
                query -> query.sortBy(NEWEST_FIRST).limit(MAP_LIMIT + 1).all());
        IssueResponseMapper.Labels labels = mapper.labels();
        List<MapResponse.Point> points = found.stream()
                .limit(MAP_LIMIT)
                .map(issue -> mapper.mapPoint(issue, labels))
                .toList();
        return new MapResponse(points, found.size() > MAP_LIMIT);
    }

    public IssueDetailResponse get(UUID id, Caller caller) {
        Issue issue = issues.findById(id).orElseThrow(() -> ApiException.notFound("Issue"));
        return mapper.detail(issue, caller);
    }

    private List<SimilarIssueResponse> similar(double latitude, double longitude, UUID categoryId, String title,
            UUID excludeId) {
        Instant since = clock.instant().minus(Duration.ofDays(duplicates.windowDays()));
        List<Object[]> rows = issues.findSimilar(latitude, longitude, categoryId, title, duplicates.radiusMeters(),
                since, excludeId, duplicates.maxResults());
        Map<UUID, Issue> found = issues.findAllById(rows.stream().map(row -> (UUID) row[0]).toList()).stream()
                .collect(Collectors.toMap(Issue::getId, Function.identity()));
        Map<UUID, IssueSummaryResponse> summaries = mapper.summaries(found.values(), Caller.from(null));
        return rows.stream()
                .filter(row -> found.containsKey((UUID) row[0]))
                .map(row -> new SimilarIssueResponse(summaries.get((UUID) row[0]),
                        (int) Math.round(((Number) row[1]).doubleValue()),
                        Math.round(((Number) row[2]).doubleValue() * 100) / 100.0))
                .toList();
    }

    private PageResponse<IssueSummaryResponse> page(IssueFilter filter, Pageable pageable, Caller viewer) {
        // Fixed order: client-supplied sort properties are ignored
        Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST);
        Page<Issue> result = issues.findAll(IssueSpecifications.matching(filter), newestFirst);
        Map<UUID, IssueSummaryResponse> summaries = mapper.summaries(result.getContent(), viewer);
        return PageResponse.of(result, issue -> summaries.get(issue.getId()));
    }

    private IssueFilter toFilter(IssueSearchParams params) {
        UUID categoryId = null;
        if (params.category() != null && !params.category().isBlank()) {
            categoryId = categories.findByCode(params.category().trim())
                    .map(CategoryResponse::id)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_CATEGORY",
                            "Category '" + params.category() + "' does not exist."));
        }
        if (params.from() != null && params.to() != null && !params.from().isBefore(params.to())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "'from' must be before 'to'.");
        }
        BoundingBox box = params.bbox() == null || params.bbox().isBlank() ? null : BoundingBox.parse(params.bbox());
        return new IssueFilter(params.status(), categoryId, params.department(), params.priority(), params.from(),
                params.to(), params.q(), null, null,
                box == null ? null : box.minLongitude(), box == null ? null : box.minLatitude(),
                box == null ? null : box.maxLongitude(), box == null ? null : box.maxLatitude(),
                Boolean.TRUE.equals(params.overdue()) ? clock.instant() : null);
    }
}
