package com.civsetu.common.validation;

/**
 * Shared regular expressions for Bean Validation {@code @Pattern} constraints.
 */
public final class ValidationPatterns {

    /** Optional leading +, then 7 to 15 digits (E.164-like). */
    public static final String PHONE = "^\\+?[0-9]{7,15}$";
    public static final String PHONE_MESSAGE = "must be 7 to 15 digits, optionally starting with +";

    /** Stable upper-case identifier such as ROADS or STREET_LIGHT. */
    public static final String CODE = "^[A-Z][A-Z0-9_]{1,49}$";
    public static final String CODE_MESSAGE = "must be 2-50 upper-case letters, digits or _, starting with a letter";

    private ValidationPatterns() {
    }
}
