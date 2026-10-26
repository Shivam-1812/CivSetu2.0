package com.civsetu.notification.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.civsetu.notification.config.NotificationProperties;
import com.civsetu.notification.dto.NotificationResponse;

/**
 * Open Server-Sent Events streams, per user. New notifications are pushed to every open tab of
 * the recipient; a comment line is sent regularly so proxies keep idle streams open. Streams live
 * in memory, so this works for a single server instance (enough for this project's deployment).
 */
@Component
class NotificationStreams implements DisposableBean {

    static final String EVENT_NAME = "notification";
    private static final Logger log = LoggerFactory.getLogger(NotificationStreams.class);

    private final Map<UUID, List<SseEmitter>> streams = new ConcurrentHashMap<>();
    private final NotificationProperties properties;
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    NotificationStreams(NotificationProperties properties) {
        this.properties = properties;
        long seconds = properties.heartbeat().toSeconds();
        heartbeat.scheduleAtFixedRate(this::sendHeartbeat, seconds, seconds, TimeUnit.SECONDS);
    }

    /** Opens a stream for the user and first sends the notifications they missed (if any). */
    SseEmitter open(UUID userId, List<NotificationResponse> missed) {
        SseEmitter emitter = new SseEmitter(properties.streamTimeout().toMillis());
        List<SseEmitter> userStreams = streams.computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>());
        userStreams.add(emitter);
        while (userStreams.size() > properties.maxStreamsPerUser()) {
            SseEmitter oldest = userStreams.remove(0);
            oldest.complete();
        }
        Runnable remove = () -> userStreams.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());

        try {
            emitter.send(SseEmitter.event().comment("connected"));
            for (NotificationResponse notification : missed) {
                emitter.send(event(notification));
            }
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    /** Sends to every open stream of the user. A broken stream is closed; the browser reconnects. */
    void push(UUID userId, NotificationResponse notification) {
        for (SseEmitter emitter : streams.getOrDefault(userId, List.of())) {
            try {
                emitter.send(event(notification));
            } catch (IOException | IllegalStateException e) {
                emitter.completeWithError(e);
            }
        }
    }

    int openStreams(UUID userId) {
        return streams.getOrDefault(userId, List.of()).size();
    }

    private void sendHeartbeat() {
        streams.values().forEach(userStreams -> userStreams.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("keep-alive"));
            } catch (IOException | IllegalStateException e) {
                emitter.completeWithError(e);
            }
        }));
    }

    private static SseEmitter.SseEventBuilder event(NotificationResponse notification) {
        // The id lets the browser send Last-Event-ID when it reconnects, so missed events are replayed
        return SseEmitter.event()
                .id(notification.id().toString())
                .name(EVENT_NAME)
                .data(notification, MediaType.APPLICATION_JSON);
    }

    @Override
    public void destroy() {
        heartbeat.shutdownNow();
        streams.values().forEach(userStreams -> userStreams.forEach(SseEmitter::complete));
        log.debug("Closed all notification streams");
    }
}
