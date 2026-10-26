package com.civsetu.issue.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.civsetu.issue.dto.IssueStatusChangedEvent;
import com.civsetu.issue.entity.Issue;
import com.civsetu.issue.entity.IssueStatus;
import com.civsetu.issue.entity.IssueUpdate;
import com.civsetu.issue.repository.IssueUpdateRepository;

/**
 * Appends steps to an issue's history and announces status changes. Every status change in the
 * system goes through here, so the history and the events can never disagree.
 */
@Component
class IssueHistory {

    private final IssueUpdateRepository updates;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    IssueHistory(IssueUpdateRepository updates, ApplicationEventPublisher events, Clock clock) {
        this.updates = updates;
        this.events = events;
        this.clock = clock;
    }

    IssueUpdate record(Issue issue, UUID actorId, IssueUpdate.ActorRole role, IssueUpdate.Action action,
            IssueStatus from, String remarks) {
        return record(new IssueUpdate(issue.getId(), actorId, role, action, from, issue.getStatus(),
                issue.getDepartmentId(), remarks), issue);
    }

    IssueUpdate record(IssueUpdate update, Issue issue) {
        IssueUpdate saved = updates.save(update);
        if (update.getFromStatus() != update.getToStatus()) {
            events.publishEvent(new IssueStatusChangedEvent(issue.getId(), issue.getReporterId(),
                    update.getFromStatus(), update.getToStatus(), issue.getDepartmentId(),
                    issue.getAssignedAuthorityId(), clock.instant()));
        }
        return saved;
    }
}
