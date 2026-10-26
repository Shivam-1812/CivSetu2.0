package com.civsetu.survey.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import org.locationtech.jts.geom.Point;

import com.civsetu.common.persistence.BaseEntity;

/**
 * A short questionnaire for the people who reported issues in one area.
 */
@Entity
@Table(name = "surveys")
public class Survey extends BaseEntity {

    @Column(nullable = false, length = 150, updatable = false)
    private String title;

    @Column(length = 2000, updatable = false)
    private String description;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    // Set for Authority surveys: only that department's issues define the audience
    @Column(name = "department_id", updatable = false)
    private UUID departmentId;

    @Column(nullable = false, columnDefinition = "geometry(Point,4326)", updatable = false)
    private Point center;

    @Column(name = "radius_m", nullable = false, updatable = false)
    private int radiusMeters;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SurveyStatus status = SurveyStatus.OPEN;

    @Column(name = "closes_at", nullable = false)
    private Instant closesAt;

    // Questions belong to the survey: saved with it, never changed afterwards
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "survey_id", nullable = false, updatable = false)
    @OrderBy("position")
    private List<SurveyQuestion> questions = new ArrayList<>();

    protected Survey() {
    }

    public Survey(String title, String description, UUID createdBy, UUID departmentId, Point center,
            int radiusMeters, Instant closesAt, List<SurveyQuestion> questions) {
        this.title = title;
        this.description = description;
        this.createdBy = createdBy;
        this.departmentId = departmentId;
        this.center = center;
        this.radiusMeters = radiusMeters;
        this.closesAt = closesAt;
        this.questions.addAll(questions);
    }

    /** Accepting answers: open and not past the closing time. */
    public boolean isAcceptingAnswers(Instant now) {
        return status == SurveyStatus.OPEN && now.isBefore(closesAt);
    }

    public void close(Instant at) {
        this.status = SurveyStatus.CLOSED;
        if (at.isBefore(closesAt)) {
            this.closesAt = at;
        }
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public double getCenterLatitude() {
        return center.getY();
    }

    public double getCenterLongitude() {
        return center.getX();
    }

    public int getRadiusMeters() {
        return radiusMeters;
    }

    public SurveyStatus getStatus() {
        return status;
    }

    public Instant getClosesAt() {
        return closesAt;
    }

    public List<SurveyQuestion> getQuestions() {
        return questions;
    }
}
