package com.civsetu.analytics.dto;

/**
 * A map cell (about 500 m across) with many reports in the period.
 */
public record Hotspot(double latitude, double longitude, long reported, long stillOpen) {
}
