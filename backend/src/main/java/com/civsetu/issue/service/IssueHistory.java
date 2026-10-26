package com.civsetu.issue.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.civsetu.issue.dto.IssueActivityEvent;
import com.civsetu.issue.entity.Issue;
import com.civsetu.issue.entity.IssueStatus;
import com.civsetu.issue.entity.IssueUpdate;
import com.civsetu.issue.repository.IssueUpdateRepository;

/**
 * Appends steps to an issue's history and announces each one. Every change in the system goes
 * through here, so the history and the events can never disagree.
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
        events.publishEvent(new IssueActivityEvent(issue.getId(), issue.getTitle(), update.getAction(),
                update.getFromStatus(), update.getToStatus(), issue.getReporterId(), issue.getDepartmentId(),
                issue.getAssignedAuthorityId(), update.getActorId(), update.getRemarks(), clock.instant()));
        return saved;
    }
}
