package com.civsetu.triage.entity;

/**
 * Who made a routing decision: the AI advisor, or the rule engine (no AI configured, or the
 * AI answer was unusable).
 */
public enum TriageSource {
    AI,
    RULE
}
