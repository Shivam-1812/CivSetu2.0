package com.civsetu.survey.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.civsetu.survey.entity.QuestionType;

/**
 * A survey for one area, usually a cluster from GET /surveys/clusters. Everyone who reported an
 * open issue within the radius is invited (for an Authority: issues of their department only).
 */
public record CreateSurveyRequest(
        @NotBlank @Size(max = 150) String title,
        @Size(max = 2000) String description,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double centerLatitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double centerLongitude,
        @NotNull @Min(50) @Max(5000) Integer radiusMeters,
        @NotNull @Future Instant closesAt,
        @NotEmpty @Size(max = 10) List<@Valid NewQuestion> questions) {

    public record NewQuestion(@NotBlank @Size(max = 300) String text, @NotNull QuestionType type) {
    }
}
