package com.civsetu.issue.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Starts triage once a report (or a return for re-routing) is committed. If the work is lost
 * (full queue, restart), the safety-net sweep picks the issue up later.
 */
@Component
class TriageTrigger {

    private static final Logger log = LoggerFactory.getLogger(TriageTrigger.class);

    private final IssueTriageService triage;
    private final TaskExecutor executor;

    TriageTrigger(IssueTriageService triage, @Qualifier("triageExecutor") TaskExecutor executor) {
        this.triage = triage;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onReported(IssueReportedEvent event) {
        try {
            executor.execute(() -> {
                try {
                    triage.triage(event.issueId());
                } catch (RuntimeException e) {
                    log.error("Triage failed for issue {}; the safety net will retry", event.issueId(), e);
                }
            });
        } catch (RuntimeException e) {
            log.warn("Triage queue full; issue {} will be picked up by the safety net", event.issueId(), e);
        }
    }
}
