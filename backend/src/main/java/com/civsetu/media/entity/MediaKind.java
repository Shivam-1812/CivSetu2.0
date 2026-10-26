package com.civsetu.media.entity;

/**
 * What a file is for. Which kinds are public is decided by the issue feature's access policy.
 */
public enum MediaKind {
    /** Photo attached to a report. */
    IMAGE,
    /** Voice note attached to a report. */
    VOICE,
    /** Work plan document (Phase 5). */
    PLAN_DOC,
    /** Photo proving the issue was fixed (Phase 5). */
    RESOLUTION_PROOF,
    /** Evidence for a rejection (Phase 5). */
    REJECTION_PROOF
}
