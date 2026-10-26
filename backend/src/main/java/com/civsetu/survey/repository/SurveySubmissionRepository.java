package com.civsetu.survey.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.civsetu.survey.entity.SurveySubmission;

public interface SurveySubmissionRepository extends JpaRepository<SurveySubmission, UUID> {

    boolean existsBySurveyIdAndUserId(UUID surveyId, UUID userId);

    long countBySurveyId(UUID surveyId);
}
