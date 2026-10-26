package com.civsetu.media.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.civsetu.common.error.ApiException;
import com.civsetu.common.security.Caller;
import com.civsetu.media.config.MediaProperties;
import com.civsetu.media.dto.MediaBytes;
import com.civsetu.media.dto.MediaContent;
import com.civsetu.media.dto.MediaResponse;
import com.civsetu.media.entity.IssueMedia;
import com.civsetu.media.entity.MediaKind;
import com.civsetu.media.repository.IssueMediaRepository;
import com.civsetu.media.storage.MediaStorage;

/**
 * Public entry point to the media feature: validating, storing and serving files attached to issues.
 */
@Service
@Transactional(readOnly = true)
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    private final IssueMediaRepository media;
    private final MediaStorage storage;
    private final ImageSanitizer images;
    private final MediaAccessPolicy access;
    private final MediaProperties properties;

    MediaService(IssueMediaRepository media, MediaStorage storage, ImageSanitizer images, MediaAccessPolicy access,
            MediaProperties properties) {
        this.media = media;
        this.storage = storage;
        this.images = images;
        this.access = access;
        this.properties = properties;
    }

    /**
     * Validates, stores and records files for an issue, inside the caller's transaction. Every
     * file is checked before any is stored; if the transaction later rolls back, the stored files
     * are deleted again. Empty parts are ignored.
     */
    @Transactional
    public List<MediaResponse> attach(UUID issueId, UUID uploaderId, MediaKind kind, List<MultipartFile> files) {
        return attach(issueId, null, uploaderId, kind, files);
    }

    /** As {@link #attach(UUID, UUID, MediaKind, List)}, linking the files to a workflow step. */
    @Transactional
    public List<MediaResponse> attach(UUID issueId, UUID updateId, UUID uploaderId, MediaKind kind,
            List<MultipartFile> files) {
        List<MultipartFile> uploads = files == null ? List.of()
                : files.stream().filter(file -> file != null && !file.isEmpty()).toList();
        if (uploads.isEmpty()) {
            return List.of();
        }
        int limit = kind == MediaKind.VOICE ? properties.maxVoiceNotes() : properties.maxImages();
        if (uploads.size() > limit) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_FILES",
                    "At most " + limit + " " + kind.name().toLowerCase() + " file(s) are allowed.");
        }
        List<FileType> types = uploads.stream().map(file -> check(file, kind)).toList();

        List<MediaResponse> stored = new ArrayList<>();
        for (int i = 0; i < uploads.size(); i++) {
            stored.add(store(issueId, updateId, uploaderId, kind, uploads.get(i), types.get(i)));
        }
        return stored;
    }

    public List<MediaResponse> listForIssue(UUID issueId) {
        return media.findByIssueIdOrderByCreatedAtAsc(issueId).stream().map(MediaResponse::from).toList();
    }

    /** The first report photos of an issue, for automatic analysis (no access check: system use only). */
    public List<MediaBytes> readReportImages(UUID issueId, int limit) {
        return media.findByIssueIdAndKindOrderByCreatedAtAsc(issueId, MediaKind.IMAGE).stream()
                .limit(limit)
                .map(file -> {
                    try (InputStream in = storage.open(file.getStorageKey())) {
                        return new MediaBytes(file.getContentType(), in.readAllBytes());
                    } catch (IOException e) {
                        throw new UncheckedIOException("Could not read " + file.getStorageKey(), e);
                    }
                })
                .toList();
    }

    /** Opens a file for download after checking that the caller may see it. */
    public MediaContent open(UUID mediaId, Caller caller) {
        IssueMedia file = media.findById(mediaId).orElseThrow(() -> ApiException.notFound("File"));
        if (!access.canView(file.getIssueId(), file.getKind(), caller)) {
            throw caller.isAnonymous()
                    ? new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Sign in to view this file.")
                    : new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have access to this file.");
        }
        String key = file.getStorageKey();
        return new MediaContent(storage.open(key), file.getContentType(), file.getSizeBytes(),
                key.substring(key.lastIndexOf('/') + 1));
    }

    private FileType check(MultipartFile file, MediaKind kind) {
        if (file.getSize() > properties.maxFileSize().toBytes()) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE",
                    "Each file may be at most " + properties.maxFileSize().toMegabytes() + " MB.");
        }
        Set<FileType.Family> allowed = allowedFamilies(kind);
        return FileType.detect(readHead(file))
                .filter(type -> allowed.contains(type.family()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE",
                        "This file type is not accepted here. Allowed: " + describe(allowed) + "."));
    }

    private MediaResponse store(UUID issueId, UUID updateId, UUID uploaderId, MediaKind kind, MultipartFile file,
            FileType type) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read upload", e);
        }
        if (type.family() == FileType.Family.IMAGE) {
            ImageSanitizer.Sanitized clean = images.sanitize(bytes, type);
            bytes = clean.bytes();
            type = clean.type();
        }
        String key = "issues/" + issueId + "/" + UUID.randomUUID() + "." + type.extension();
        storage.put(key, bytes, type.contentType());
        deleteIfRolledBack(key);
        IssueMedia saved = media.save(new IssueMedia(issueId, updateId, uploaderId, kind, key, type.contentType(),
                bytes.length));
        return MediaResponse.from(saved);
    }

    private void deleteIfRolledBack(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        storage.delete(key);
                    } catch (RuntimeException e) {
                        log.warn("Could not remove orphaned media {}", key, e);
                    }
                }
            }
        });
    }

    private static byte[] readHead(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(FileType.HEADER_BYTES);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read upload", e);
        }
    }

    private static Set<FileType.Family> allowedFamilies(MediaKind kind) {
        return switch (kind) {
            case VOICE -> EnumSet.of(FileType.Family.AUDIO);
            case PLAN_DOC -> EnumSet.of(FileType.Family.IMAGE, FileType.Family.DOCUMENT);
            case IMAGE, RESOLUTION_PROOF, REJECTION_PROOF -> EnumSet.of(FileType.Family.IMAGE);
        };
    }

    private static String describe(Set<FileType.Family> families) {
        List<String> names = new ArrayList<>();
        for (FileType type : FileType.values()) {
            if (families.contains(type.family())) {
                names.add(type.extension());
            }
        }
        return String.join(", ", names);
    }
}
