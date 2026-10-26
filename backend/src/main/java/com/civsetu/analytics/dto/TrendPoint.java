package com.civsetu.analytics.dto;

import java.time.LocalDate;

/**
 * One day (in the city's time zone): issues reported and issues resolved that day.
 */
public record TrendPoint(LocalDate day, long reported, long resolved) {
}
