package com.civsetu.survey.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.civsetu.survey.entity.SurveyAnswer;

public interface SurveyAnswerRepository extends JpaRepository<SurveyAnswer, UUID> {

    List<SurveyAnswer> findByQuestionIdIn(Collection<UUID> questionIds);
}
