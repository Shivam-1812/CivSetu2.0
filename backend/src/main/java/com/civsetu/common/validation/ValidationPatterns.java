package com.civsetu.common.validation;

/**
 * Shared regular expressions for Bean Validation {@code @Pattern} constraints.
 */
public final class ValidationPatterns {

    /** Optional leading +, then 7 to 15 digits (E.164-like). */
    public static final String PHONE = "^\\+?[0-9]{7,15}$";
    public static final String PHONE_MESSAGE = "must be 7 to 15 digits, optionally starting with +";

    private ValidationPatterns() {
    }
}
