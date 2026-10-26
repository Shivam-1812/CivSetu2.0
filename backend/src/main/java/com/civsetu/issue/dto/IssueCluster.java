package com.civsetu.issue.dto;

/**
 * A group of open issues close to each other (each within the cluster radius of another).
 * A survey for this area uses the centre and radius.
 */
public record IssueCluster(double centerLatitude, double centerLongitude, int issueCount, int reporterCount) {
}
