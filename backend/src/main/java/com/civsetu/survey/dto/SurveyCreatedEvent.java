package com.civsetu.survey.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Published inside the transaction that creates a survey; notifications invite the people listed.
 */
public record SurveyCreatedEvent(UUID surveyId, String title, Instant closesAt, List<UUID> invitedUserIds) {
}
