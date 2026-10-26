package com.civsetu.issue.entity;

/**
 * Who decided an issue's priority: the category rules, the optional AI advisor, or an Admin.
 */
public enum PrioritySource {
    RULE,
    AI,
    ADMIN
}
