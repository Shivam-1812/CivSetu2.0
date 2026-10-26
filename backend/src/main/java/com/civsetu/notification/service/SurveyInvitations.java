package com.civsetu.notification.service;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.civsetu.notification.entity.NotificationType;
import com.civsetu.survey.dto.SurveyCreatedEvent;

/**
 * Invites the people a new survey targets. Saved together with the survey.
 */
@Component
class SurveyInvitations {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneOffset.UTC);

    private final NotificationService notifications;

    SurveyInvitations(NotificationService notifications) {
        this.notifications = notifications;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    void onSurveyCreated(SurveyCreatedEvent event) {
        for (UUID user : event.invitedUserIds()) {
            notifications.notify(user, NotificationType.SURVEY_INVITATION, "Tell us about your area",
                    "You reported an issue in an area we are reviewing. Please answer the short survey “"
                            + event.title() + "” before " + DATE.format(event.closesAt()) + ".", null);
        }
    }
}
