package com.civsetu.issue.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.civsetu.audit.service.AuditService;
import com.civsetu.issue.entity.Issue;
import com.civsetu.issue.entity.IssueUpdate;
import com.civsetu.issue.repository.IssueRepository;

/**
 * Finds open issues past their deadline and raises the alarm once per issue: a public history step
 * and notifications to the reporter, the assigned Authority and the Admins. The claim and the alert
 * are in one transaction: if anything fails, nothing is marked and the next run retries.
 */
@Component
class SlaMonitor {

    private static final Logger log = LoggerFactory.getLogger(SlaMonitor.class);
    private static final DateTimeFormatter DEADLINE = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);

    private final IssueRepository issues;
    private final IssueHistory history;
    private final AuditService audit;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final int batchSize;

    SlaMonitor(IssueRepository issues, IssueHistory history, AuditService audit, Clock clock,
            PlatformTransactionManager transactions, @Value("${civsetu.sla.batch-size:100}") int batchSize) {
        this.issues = issues;
        this.history = history;
        this.audit = audit;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactions);
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${civsetu.sla.check-interval}", initialDelayString = "${civsetu.sla.check-interval}")
    void scheduledCheck() {
        int alerted = checkBreaches();
        if (alerted > 0) {
            log.info("SLA monitor: {} issue(s) passed their deadline", alerted);
        }
    }

    /** Alerts every newly breached issue; returns how many. */
    int checkBreaches() {
        int total = 0;
        int batch;
        do {
            batch = transaction.execute(status -> alertBatch(clock.instant()));
            total += batch;
        } while (batch == batchSize);
        return total;
    }

    private int alertBatch(Instant now) {
        List<UUID> claimed = issues.claimBreached(now, batchSize);
        for (UUID id : claimed) {
            Issue issue = issues.findById(id).orElseThrow();
            history.record(issue, null, IssueUpdate.ActorRole.SYSTEM, IssueUpdate.Action.SLA_BREACHED,
                    issue.getStatus(), "The deadline of " + DEADLINE.format(issue.getSlaDueAt())
                            + " passed before the issue was resolved.");
            audit.recordSystem("SLA_BREACHED", "ISSUE", id, "deadline=" + issue.getSlaDueAt());
        }
        return claimed.size();
    }
}
