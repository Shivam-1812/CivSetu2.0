package com.civsetu.survey.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One answer per question: {@code rating} for RATING, {@code yesNo} for YES_NO, {@code text} for TEXT
 * (text questions may be skipped).
 */
public record SubmitAnswersRequest(@NotEmpty List<@Valid Answer> answers) {

    public record Answer(
            @NotNull UUID questionId,
            @Min(1) @Max(5) Integer rating,
            Boolean yesNo,
            @Size(max = 1000) String text) {
    }
}
