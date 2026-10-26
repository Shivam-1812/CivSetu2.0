package com.civsetu.survey.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.civsetu.common.persistence.BaseEntity;

@Entity
@Table(name = "survey_questions")
public class SurveyQuestion extends BaseEntity {

    @Column(nullable = false, updatable = false)
    private int position;

    @Column(nullable = false, length = 300, updatable = false)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private QuestionType type;

    protected SurveyQuestion() {
    }

    public SurveyQuestion(int position, String text, QuestionType type) {
        this.position = position;
        this.text = text;
        this.type = type;
    }

    public int getPosition() {
        return position;
    }

    public String getText() {
        return text;
    }

    public QuestionType getType() {
        return type;
    }
}
