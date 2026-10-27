package com.civsetu.issue.service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;

import com.civsetu.common.error.ApiException;
import com.civsetu.common.security.Caller;
import com.civsetu.issue.entity.Issue;
import com.civsetu.issue.entity.IssueStatus;

/**
 * The workflow rules in one table: for each action, which statuses it may start from and who may
 * perform it. Routing (REPORTED → ASSIGNED) is done by the system only and is not listed here, so
 * no person, including Admin, can route, prioritise or reassign an issue.
 */
final class TransitionPolicy {

    enum Action { PLAN, RESOLVE, REJECT, RETURN, MARK_DUPLICATE, REOPEN }

    enum Actor {
        /** An Authority of the issue's department (the system assigns one of them as the lead). */
        DEPARTMENT_AUTHORITY,
        /** The citizen who reported the issue. */
        REPORTER
    }

    record Rule(Set<IssueStatus> from, IssueStatus to, Actor actor) {
    }

    static final Map<Action, Rule> RULES = new EnumMap<>(Map.of(
            Action.MARK_DUPLICATE, new Rule(EnumSet.of(IssueStatus.ASSIGNED, IssueStatus.IN_PROGRESS),
                    IssueStatus.DUPLICATE, Actor.DEPARTMENT_AUTHORITY),
            Action.PLAN, new Rule(EnumSet.of(IssueStatus.ASSIGNED), IssueStatus.IN_PROGRESS, Actor.DEPARTMENT_AUTHORITY),
            Action.RESOLVE, new Rule(EnumSet.of(IssueStatus.IN_PROGRESS), IssueStatus.RESOLVED,
                    Actor.DEPARTMENT_AUTHORITY),
            Action.REJECT, new Rule(EnumSet.of(IssueStatus.ASSIGNED, IssueStatus.IN_PROGRESS), IssueStatus.REJECTED,
                    Actor.DEPARTMENT_AUTHORITY),
            Action.RETURN, new Rule(EnumSet.of(IssueStatus.ASSIGNED, IssueStatus.IN_PROGRESS), IssueStatus.REPORTED,
                    Actor.DEPARTMENT_AUTHORITY),
            Action.REOPEN, new Rule(EnumSet.of(IssueStatus.RESOLVED, IssueStatus.REJECTED, IssueStatus.DUPLICATE),
                    IssueStatus.ASSIGNED, Actor.REPORTER)));

    private TransitionPolicy() {
    }

    /** Throws 403 if the caller may not perform the action, or 409 if the status does not allow it. */
    static void check(Action action, Issue issue, Caller caller) {
        Rule rule = RULES.get(action);
        boolean allowed = switch (rule.actor()) {
            case DEPARTMENT_AUTHORITY -> caller.isAuthorityOf(issue.getDepartmentId());
            case REPORTER -> caller.is(issue.getReporterId());
        };
        if (!allowed) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_ALLOWED", rule.actor() == Actor.REPORTER
                    ? "Only the citizen who reported this issue can do this."
                    : "Only an Authority of this issue's department can do this.");
        }
        if (!rule.from().contains(issue.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_TRANSITION",
                    "Cannot " + action.name().toLowerCase() + " an issue that is " + issue.getStatus() + ".");
        }
    }
}
