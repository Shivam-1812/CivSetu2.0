package com.civsetu.media.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

/**
 * Stores files under a root folder. Meant for development; use S3-compatible storage in production.
 * Keys are checked against a strict pattern and resolved inside the root only, so a key can never
 * point outside it (path traversal).
 */
public class LocalDiskStorage implements MediaStorage {

    private static final Pattern SAFE_KEY = Pattern.compile("^[a-z0-9-]+(/[a-z0-9-]+)*\\.[a-z0-9]+$");

    private final Path root;

    public LocalDiskStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create media folder " + this.root, e);
        }
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            // Write to a temporary file first so readers never see a half-written file
            Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            Files.write(temp, content);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot store media " + key, e);
        }
    }

    @Override
    public InputStream open(String key) {
        try {
            return Files.newInputStream(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read media " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete media " + key, e);
        }
    }

    private Path resolve(String key) {
        if (!SAFE_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid media key: " + key);
        }
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Media key escapes the storage root: " + key);
        }
        return path;
    }
}
