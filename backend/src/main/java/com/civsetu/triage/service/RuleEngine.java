package com.civsetu.triage.service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.civsetu.common.domain.Priority;
import com.civsetu.department.dto.CategoryResponse;

/**
 * Transparent fallback rules: the category's default priority, raised one level when the
 * report mentions an immediate danger. Words are matched whole, so "latest" never matches "test"
 * and "fireworks" never matches "fire".
 */
@Component
class RuleEngine {

    private static final Pattern DANGER = Pattern.compile(
            "\\b(electrocut\\w*|live wires?|sparking|sparks|fire|smoke|gas leak(ing)?|collaps\\w*|injur\\w*"
                    + "|accident|open manhole|sewage overflow(ing)?|overflowing sewage|bleeding)\\b",
            Pattern.CASE_INSENSITIVE);

    record Outcome(Priority priority, String reason) {
    }

    Outcome priorityFor(CategoryResponse category, String title, String description) {
        Priority base = category.defaultPriority();
        Matcher danger = DANGER.matcher(title + "\n" + description);
        if (danger.find() && base != Priority.CRITICAL) {
            Priority raised = Priority.values()[base.ordinal() - 1];
            return new Outcome(raised, "Category '" + category.label() + "' is normally " + label(base)
                    + " priority; raised to " + label(raised) + " because the report mentions '"
                    + danger.group().toLowerCase(Locale.ROOT) + "'.");
        }
        return new Outcome(base, "Category '" + category.label() + "' is " + label(base) + " priority.");
    }

    private static String label(Priority priority) {
        return priority.name().toLowerCase(Locale.ROOT);
    }
}
