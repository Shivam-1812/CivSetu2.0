package com.civsetu.media.controller;

import java.time.Duration;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.civsetu.common.security.Caller;
import com.civsetu.media.dto.MediaContent;
import com.civsetu.media.service.MediaService;

/**
 * Streams stored files. Public photos need no token; private files (voice notes, plans,
 * rejection evidence) need the reporter's or staff's token, so browsers fetch them with
 * the Authorization header rather than a plain {@code <img src>}.
 */
@RestController
@RequestMapping("/api/v1/media")
class MediaController {

    private final MediaService media;

    MediaController(MediaService media) {
        this.media = media;
    }

    @GetMapping("/{id}")
    ResponseEntity<InputStreamResource> download(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        MediaContent content = media.open(id, Caller.from(jwt));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(content.sizeBytes())
                // Files never change once stored; "private" keeps them out of shared caches
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(content.fileName()).build().toString())
                .body(new InputStreamResource(content.stream()));
    }
}
