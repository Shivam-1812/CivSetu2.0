package com.civsetu.issue.service;

import java.util.UUID;

/**
 * An issue needs triage: it was just reported, or a department returned it for re-routing.
 */
record IssueReportedEvent(UUID issueId) {
}
