package com.civsetu.notification.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.stereotype.Component;

import com.civsetu.notification.config.NotificationProperties;

/**
 * Short-lived, single-use tickets that stand in for the access token on the stream URL, so the
 * real token never appears in URLs or server logs.
 */
@Component
class StreamTickets {

    private final SecureRandom random = new SecureRandom();
    private final Cache<String, UUID> tickets;

    StreamTickets(NotificationProperties properties) {
        this.tickets = Caffeine.newBuilder()
                .expireAfterWrite(properties.ticketTtl())
                .maximumSize(100_000)
                .build();
    }

    String issue(UUID userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(ticket, userId);
        return ticket;
    }

    /** The ticket's user, once; the ticket cannot be used again. */
    Optional<UUID> redeem(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return Optional.empty();
        }
        UUID userId = tickets.asMap().remove(ticket);
        return Optional.ofNullable(userId);
    }
}
