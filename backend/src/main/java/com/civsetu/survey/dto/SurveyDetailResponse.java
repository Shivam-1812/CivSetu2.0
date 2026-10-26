package com.civsetu.survey.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.civsetu.survey.entity.QuestionType;
import com.civsetu.survey.entity.SurveyStatus;

/**
 * A survey as shown to invited people and to its owner.
 */
public record SurveyDetailResponse(
        UUID id,
        String title,
        String description,
        SurveyStatus status,
        Instant closesAt,
        double centerLatitude,
        double centerLongitude,
        int radiusMeters,
        long invitedCount,
        long responseCount,
        boolean answeredByMe,
        List<Question> questions) {

    public record Question(UUID id, int position, String text, QuestionType type) {
    }
}
