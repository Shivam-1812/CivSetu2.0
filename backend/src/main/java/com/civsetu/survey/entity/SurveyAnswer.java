package com.civsetu.survey.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.civsetu.common.persistence.BaseEntity;

/**
 * The answer to one question. Exactly one of rating, yesNo or text is set, matching the question type.
 */
@Entity
@Table(name = "survey_answers")
public class SurveyAnswer extends BaseEntity {

    @Column(name = "response_id", nullable = false, updatable = false)
    private UUID submissionId;

    @Column(name = "question_id", nullable = false, updatable = false)
    private UUID questionId;

    @Column(updatable = false)
    private Integer rating;

    @Column(name = "yes_no", updatable = false)
    private Boolean yesNo;

    @Column(length = 1000, updatable = false)
    private String text;

    protected SurveyAnswer() {
    }

    public SurveyAnswer(UUID submissionId, UUID questionId, Integer rating, Boolean yesNo, String text) {
        this.submissionId = submissionId;
        this.questionId = questionId;
        this.rating = rating;
        this.yesNo = yesNo;
        this.text = text;
    }

    public UUID getQuestionId() {
        return questionId;
    }

    public Integer getRating() {
        return rating;
    }

    public Boolean getYesNo() {
        return yesNo;
    }

    public String getText() {
        return text;
    }
}
