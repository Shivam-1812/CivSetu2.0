package com.civsetu.survey.entity;

public enum QuestionType {
    /** 1 (very poor) to 5 (very good). */
    RATING,
    YES_NO,
    /** Free text; the only optional kind of answer. */
    TEXT
}
