package com.civsetu.survey.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.civsetu.survey.entity.QuestionType;

/**
 * Aggregated answers, for the survey's owner and Admins. Individual answers are not linked to people.
 *
 * @param ratingCounts how many people chose each rating 1-5 (RATING questions)
 * @param textAnswers  the free-text answers, without names (TEXT questions)
 */
public record SurveyResultsResponse(UUID id, String title, long invitedCount, long responseCount,
        List<QuestionResult> questions) {

    public record QuestionResult(UUID id, String text, QuestionType type, long answered, Double averageRating,
            Map<Integer, Long> ratingCounts, Long yes, Long no, List<String> textAnswers) {
    }
}
