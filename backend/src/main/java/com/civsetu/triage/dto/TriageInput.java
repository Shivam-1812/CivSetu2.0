package com.civsetu.triage.dto;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.civsetu.media.dto.MediaBytes;

/**
 * Everything triage needs about one report.
 *
 * @param categoryId          the category the citizen picked (a hint the AI may correct)
 * @param excludedDepartments departments that already returned this issue as not theirs
 */
public record TriageInput(
        UUID issueId,
        String title,
        String description,
        UUID categoryId,
        String address,
        List<MediaBytes> images,
        Set<UUID> excludedDepartments) {
}
