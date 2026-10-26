package com.civsetu.issue.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.civsetu.engagement.dto.IssueRef;
import com.civsetu.engagement.service.IssueLookup;
import com.civsetu.issue.repository.IssueRepository;

/**
 * Gives the engagement feature the issue facts it needs, without it depending on this feature.
 */
@Component
class IssueLookupAdapter implements IssueLookup {

    private final IssueRepository issues;

    IssueLookupAdapter(IssueRepository issues) {
        this.issues = issues;
    }

    @Override
    public Optional<IssueRef> find(UUID issueId) {
        return issues.findById(issueId).map(issue -> new IssueRef(issue.getId(), issue.getReporterId(),
                issue.getTitle()));
    }
}
