package com.civsetu.sla.dto;

/**
 * Where an issue stands against its deadline.
 */
public enum SlaState {
    /** Open, with plenty of time left. */
    ON_TRACK,
    /** Open, and less than the at-risk share of its time is left. */
    AT_RISK,
    /** Open, and the deadline has passed. */
    BREACHED,
    /** Resolved on or before the deadline. */
    MET,
    /** Resolved after the deadline. */
    MISSED
}
