package com.civsetu.notification.service;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.civsetu.department.dto.DepartmentResponse;
import com.civsetu.department.service.DepartmentService;
import com.civsetu.engagement.dto.CommentAddedEvent;
import com.civsetu.issue.dto.IssueActivityEvent;
import com.civsetu.notification.entity.NotificationType;
import com.civsetu.user.service.UserService;

/**
 * Turns issue activity into notifications for the people involved: the reporter hears about every
 * step of their report, the assigned Authority about new work. Nobody is notified of their own
 * action. Runs just before the change commits, so notifications are saved together with it.
 */
@Component
class IssueNotifier {

    private final NotificationService notifications;
    private final DepartmentService departments;
    private final UserService users;

    IssueNotifier(NotificationService notifications, DepartmentService departments, UserService users) {
        this.notifications = notifications;
        this.departments = departments;
        this.users = users;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    void onIssueActivity(IssueActivityEvent event) {
        String title = quoted(event.issueTitle());
        String department = departmentName(event.departmentId());
        switch (event.action()) {
            // A confirmation of one's own report is wanted, so this one ignores the "not your own action" rule
            case REPORTED -> notifications.notify(event.reporterId(), NotificationType.REPORT_RECEIVED,
                    "Report received", "We received " + title + ". It is being routed to the right department.",
                    event.issueId());
            case ASSIGNED -> {
                toReporter(event, NotificationType.ROUTED, "Sent to " + department,
                        title + " was routed to " + department + ". " + nullToEmpty(event.remarks()));
                toAuthority(event, NotificationType.ASSIGNED_TO_YOU, "New issue assigned",
                        title + " was assigned to you.");
            }
            case REASSIGNED -> toAuthority(event, NotificationType.ASSIGNED_TO_YOU, "Issue handed over to you",
                    title + " was assigned to you.");
            case PLANNED -> toReporter(event, NotificationType.WORK_STARTED, "Work has started",
                    department + " started work on " + title + ".");
            case RESOLVED -> toReporter(event, NotificationType.RESOLVED, "Issue resolved",
                    title + " was marked resolved: " + nullToEmpty(event.remarks())
                            + " If it is not fixed, you can reopen it.");
            case REJECTED -> toReporter(event, NotificationType.REJECTED, "Issue closed without action",
                    title + " was closed: " + nullToEmpty(event.remarks()));
            case RETURNED -> toReporter(event, NotificationType.RETURNED, "Being sent to another department",
                    department + " says " + title + " is not theirs. The system is routing it again.");
            case MARKED_DUPLICATE -> toReporter(event, NotificationType.DUPLICATE, "Already reported",
                    title + " is being handled under another report. " + nullToEmpty(event.remarks()));
            case REOPENED -> toAuthority(event, NotificationType.REOPENED, "Issue reopened",
                    "The reporter reopened " + title + ": " + nullToEmpty(event.remarks()));
            case SLA_BREACHED -> {
                toReporter(event, NotificationType.SLA_BREACHED, "Deadline missed",
                        "The deadline for " + title + " has passed. " + department + " has been alerted.");
                toAuthority(event, NotificationType.SLA_BREACHED, "Deadline missed",
                        title + " is past its deadline. Please resolve it as soon as possible.");
                for (UUID admin : users.activeAdminIds()) {
                    notifications.notify(admin, NotificationType.SLA_BREACHED, "Deadline missed",
                            title + " (" + department + ") is past its deadline.", event.issueId());
                }
            }
            case PRIORITY_RAISED -> {
                toReporter(event, NotificationType.PRIORITY_RAISED, "Priority raised", nullToEmpty(event.remarks()));
                toAuthority(event, NotificationType.PRIORITY_RAISED, "Priority raised",
                        title + ": " + nullToEmpty(event.remarks()));
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    void onComment(CommentAddedEvent event) {
        if (event.reporterId() != null && !event.reporterId().equals(event.authorId())) {
            notifications.notify(event.reporterId(), NotificationType.NEW_COMMENT, "New comment on your report",
                    (event.authorName() == null ? "Someone" : event.authorName()) + " commented on "
                            + quoted(event.issueTitle()) + ".", event.issueId());
        }
    }

    private void toReporter(IssueActivityEvent event, NotificationType type, String title, String body) {
        notifyUnlessActor(event.reporterId(), event, type, title, body);
    }

    private void toAuthority(IssueActivityEvent event, NotificationType type, String title, String body) {
        notifyUnlessActor(event.assignedAuthorityId(), event, type, title, body);
    }

    private void notifyUnlessActor(UUID recipient, IssueActivityEvent event, NotificationType type, String title,
            String body) {
        if (recipient != null && !recipient.equals(event.actorId())) {
            notifications.notify(recipient, type, title, body.trim(), event.issueId());
        }
    }

    private String departmentName(UUID departmentId) {
        DepartmentResponse department = departmentId == null ? null : departments.lookup().get(departmentId);
        return department == null ? "the department" : department.name();
    }

    private static String quoted(String title) {
        return "“" + title + "”";
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text.trim();
    }
}
