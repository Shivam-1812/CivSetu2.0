package com.civsetu.media.dto;

/**
 * A stored file's bytes, e.g. a report photo handed to the triage advisor.
 */
public record MediaBytes(String contentType, byte[] bytes) {
}
