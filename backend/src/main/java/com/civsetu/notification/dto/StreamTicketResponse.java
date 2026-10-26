package com.civsetu.notification.dto;

/**
 * A one-time ticket for opening the live stream. Browsers cannot send an Authorization header
 * with {@code EventSource}, so they exchange their token for this ticket first and put it in the URL.
 */
public record StreamTicketResponse(String ticket, long expiresInSeconds, String streamUrl) {
}
