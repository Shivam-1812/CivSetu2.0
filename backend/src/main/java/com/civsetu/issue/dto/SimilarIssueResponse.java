package com.civsetu.issue.dto;

/**
 * An open issue that may describe the same problem.
 *
 * @param distanceMeters  straight-line distance between the two locations
 * @param titleSimilarity 0 (nothing in common) to 1 (identical), by trigram comparison
 */
public record SimilarIssueResponse(IssueSummaryResponse issue, int distanceMeters, double titleSimilarity) {
}
