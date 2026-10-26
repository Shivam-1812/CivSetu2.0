package com.civsetu.triage.service;

import java.util.List;

import com.civsetu.department.dto.CategoryResponse;
import com.civsetu.department.dto.DepartmentResponse;
import com.civsetu.media.dto.MediaBytes;

/**
 * An AI service that suggests routing for a report. Its answer is only a suggestion:
 * {@link TriageService} checks it and falls back to the rule engine when it is unusable.
 * Implementations may throw on errors or timeouts; that also triggers the fallback.
 */
public interface TriageAdvisor {

    Answer advise(Request request);

    /** Model name recorded with each decision, e.g. "gemini-3.5-flash-lite". */
    String model();

    /**
     * @param categories  active categories the AI may choose from
     * @param departments departments the AI may choose from (excludes ones that returned the issue)
     */
    record Request(String title, String description, String categoryHint, String address, List<MediaBytes> images,
            List<CategoryResponse> categories, List<DepartmentResponse> departments) {
    }

    /** The AI's suggestion; any field may be missing or invalid and is checked before use. */
    record Answer(String categoryCode, String departmentCode, String priority, Double confidence, String reason,
            Boolean looksInvalid) {
    }
}
