package com.civsetu.survey.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.civsetu.common.persistence.BaseEntity;

/**
 * One person's completed survey (table survey_responses; one per person per survey).
 */
@Entity
@Table(name = "survey_responses")
public class SurveySubmission extends BaseEntity {

    @Column(name = "survey_id", nullable = false, updatable = false)
    private UUID surveyId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    protected SurveySubmission() {
    }

    public SurveySubmission(UUID surveyId, UUID userId) {
        this.surveyId = surveyId;
        this.userId = userId;
    }

    public UUID getSurveyId() {
        return surveyId;
    }

    public UUID getUserId() {
        return userId;
    }
}
