package com.civsetu.issue.entity;

/**
 * What decided an issue's priority: the AI advisor or the rule engine. No person can set it.
 */
public enum PrioritySource {
    RULE,
    AI
}
