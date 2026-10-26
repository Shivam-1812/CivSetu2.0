package com.civsetu.survey.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.civsetu.survey.entity.Survey;

/**
 * Surveys, and the survey_targets join table (who was invited), which has no entity of its own.
 */
public interface SurveyRepository extends JpaRepository<Survey, UUID> {

    @Modifying
    @Query(value = "INSERT INTO survey_targets (survey_id, user_id) VALUES (:surveyId, :userId) "
            + "ON CONFLICT DO NOTHING", nativeQuery = true)
    void addTarget(@Param("surveyId") UUID surveyId, @Param("userId") UUID userId);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM survey_targets WHERE survey_id = :surveyId AND user_id = :userId)",
            nativeQuery = true)
    boolean isTarget(@Param("surveyId") UUID surveyId, @Param("userId") UUID userId);

    @Query(value = "SELECT count(*) FROM survey_targets WHERE survey_id = :surveyId", nativeQuery = true)
    long countTargets(@Param("surveyId") UUID surveyId);

    /** Surveys the user is invited to, newest first. */
    @Query(value = "SELECT s.* FROM surveys s JOIN survey_targets t ON t.survey_id = s.id "
            + "WHERE t.user_id = :userId ORDER BY s.created_at DESC", nativeQuery = true)
    List<Survey> findInvitedTo(@Param("userId") UUID userId);
}
