package com.civsetu.engagement.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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

import com.civsetu.audit.service.AuditService;
import com.civsetu.common.error.ApiException;
import com.civsetu.common.security.Caller;
import com.civsetu.common.web.PageResponse;
import com.civsetu.engagement.config.EngagementProperties;
import com.civsetu.engagement.dto.CommentAddedEvent;
import com.civsetu.engagement.dto.CommentResponse;
import com.civsetu.engagement.dto.EngagementSummary;
import com.civsetu.engagement.dto.IssueRef;
import com.civsetu.engagement.dto.ModerateCommentRequest;
import com.civsetu.engagement.dto.VoteTally;
import com.civsetu.engagement.dto.VoteTallyChangedEvent;
import com.civsetu.engagement.entity.IssueComment;
import com.civsetu.engagement.entity.VoteValue;
import com.civsetu.engagement.repository.IssueCommentRepository;
import com.civsetu.engagement.repository.IssueVoteRepository;
import com.civsetu.user.dto.UserResponse;
import com.civsetu.user.service.UserService;

/**
 * Community signals on issues: one agree/disagree vote per citizen, and public comments with a
 * per-person limit. Votes feed the system's community escalation; nobody sets priority by hand.
 */
@Service
@Transactional(readOnly = true)
public class EngagementService {

    private final IssueVoteRepository votes;
    private final IssueCommentRepository comments;
    private final IssueLookup issues;
    private final UserService users;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final EngagementProperties properties;

    EngagementService(IssueVoteRepository votes, IssueCommentRepository comments, IssueLookup issues,
            UserService users, AuditService audit, ApplicationEventPublisher events, EngagementProperties properties) {
        this.votes = votes;
        this.comments = comments;
        this.issues = issues;
        this.users = users;
        this.audit = audit;
        this.events = events;
        this.properties = properties;
    }

    /** Sets or changes the caller's vote. Repeating the same vote changes nothing. */
    @Transactional
    public VoteTally vote(UUID issueId, Caller citizen, VoteValue value) {
        IssueRef issue = require(issueId);
        if (citizen.is(issue.reporterId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OWN_ISSUE", "You cannot vote on your own report.");
        }
        votes.upsert(issueId, citizen.id(), value.name());
        return publishTally(issueId, citizen);
    }

    @Transactional
    public VoteTally removeVote(UUID issueId, Caller citizen) {
        require(issueId);
        votes.deleteVote(issueId, citizen.id());
        return publishTally(issueId, citizen);
    }

    public VoteTally tally(UUID issueId, Caller viewer) {
        long agree = 0;
        long disagree = 0;
        for (Object[] row : votes.countByValue(issueId)) {
            if (row[0] == VoteValue.AGREE) {
                agree = (Long) row[1];
            } else {
                disagree = (Long) row[1];
            }
        }
        VoteValue mine = viewer.isAnonymous() ? null : votes.findValue(issueId, viewer.id()).orElse(null);
        return new VoteTally(agree, disagree, mine);
    }

    /** Net agree votes at which the system raises an open issue's priority (once). */
    public int escalationThreshold() {
        return properties.escalationVotes();
    }

    public long commentCount(UUID issueId) {
        return comments.countByIssueId(issueId);
    }

    /** Votes, comment counts and the viewer's vote for several issues, in three queries (for list rows). */
    public Map<UUID, EngagementSummary> summaries(Collection<UUID> issueIds, Caller viewer) {
        if (issueIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, long[]> counts = new HashMap<>();
        for (Object[] row : votes.countByIssueIds(issueIds)) {
            long[] tally = counts.computeIfAbsent((UUID) row[0], id -> new long[3]);
            tally[row[1] == VoteValue.AGREE ? 0 : 1] = (Long) row[2];
        }
        for (Object[] row : comments.countByIssueIds(issueIds)) {
            counts.computeIfAbsent((UUID) row[0], id -> new long[3])[2] = (Long) row[1];
        }
        Map<UUID, VoteValue> mine = new HashMap<>();
        if (!viewer.isAnonymous()) {
            for (Object[] row : votes.findValues(issueIds, viewer.id())) {
                mine.put((UUID) row[0], (VoteValue) row[1]);
            }
        }
        Map<UUID, EngagementSummary> result = new HashMap<>();
        for (UUID id : issueIds) {
            long[] tally = counts.getOrDefault(id, new long[3]);
            result.put(id, new EngagementSummary(tally[0], tally[1], tally[2], mine.get(id)));
        }
        return result;
    }

    /** Comments oldest first, so a conversation reads top to bottom. */
    public PageResponse<CommentResponse> comments(UUID issueId, Pageable pageable) {
        require(issueId);
        Page<IssueComment> page = comments.findByIssueId(issueId, PageRequest.of(pageable.getPageNumber(),
                pageable.getPageSize(), Sort.by("seq")));
        Map<UUID, UserResponse> authors = page.getContent().stream()
                .map(IssueComment::getUserId)
                .distinct()
                .map(users::findById)
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(UserResponse::id, Function.identity()));
        return PageResponse.of(page, comment -> toResponse(comment, authors.get(comment.getUserId())));
    }

    @Transactional
    public CommentResponse addComment(UUID issueId, Caller author, String body) {
        IssueRef issue = require(issueId);
        if (comments.countByIssueIdAndUserId(issueId, author.id()) >= properties.maxCommentsPerUser()) {
            throw ApiException.conflict("COMMENT_LIMIT_REACHED",
                    "You can post at most " + properties.maxCommentsPerUser() + " comments on one issue.");
        }
        IssueComment saved = comments.save(new IssueComment(issueId, author.id(), body.trim()));
        UserResponse user = users.findById(author.id()).orElse(null);
        events.publishEvent(new CommentAddedEvent(issueId, issue.title(), issue.reporterId(), saved.getId(),
                author.id(), user == null ? null : user.name()));
        return toResponse(saved, user);
    }

    /** Admin moderation. The comment is kept; readers see that it was hidden and why. Audited. */
    @Transactional
    public CommentResponse moderate(UUID commentId, Caller admin, ModerateCommentRequest request) {
        IssueComment comment = comments.findById(commentId).orElseThrow(() -> ApiException.notFound("Comment"));
        if (request.hidden()) {
            if (request.reason() == null || request.reason().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "REASON_REQUIRED",
                        "Say why the comment is hidden; the reason is shown publicly.");
            }
            comment.hide(admin.id(), request.reason().trim());
        } else {
            comment.restore();
        }
        audit.record(admin, request.hidden() ? "COMMENT_HIDDEN" : "COMMENT_RESTORED", "COMMENT", commentId,
                request.reason());
        return toResponse(comment, users.findById(comment.getUserId()).orElse(null));
    }

    private VoteTally publishTally(UUID issueId, Caller viewer) {
        VoteTally tally = tally(issueId, viewer);
        events.publishEvent(new VoteTallyChangedEvent(issueId, tally.agree(), tally.disagree()));
        return tally;
    }

    private IssueRef require(UUID issueId) {
        return issues.find(issueId).orElseThrow(() -> ApiException.notFound("Issue"));
    }

    private static CommentResponse toResponse(IssueComment comment, UserResponse author) {
        return new CommentResponse(comment.getId(), author == null ? null : author.name(),
                author == null ? null : author.role().name(), comment.isHidden() ? null : comment.getBody(),
                comment.isHidden(), comment.getHiddenReason(), comment.getCreatedAt());
    }
}
