package com.civsetu.media.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LocalDiskStorageTest {

    @TempDir
    Path root;

    @Test
    void storesReadsAndDeletes() throws Exception {
        LocalDiskStorage storage = new LocalDiskStorage(root);
        storage.put("issues/abc-123/photo-1.jpg", new byte[] {1, 2, 3}, "image/jpeg");

        try (InputStream in = storage.open("issues/abc-123/photo-1.jpg")) {
            assertThat(in.readAllBytes()).containsExactly(1, 2, 3);
        }
        storage.delete("issues/abc-123/photo-1.jpg");
        assertThat(Files.exists(root.resolve("issues/abc-123/photo-1.jpg"))).isFalse();
        // Deleting again is not an error
        storage.delete("issues/abc-123/photo-1.jpg");
        assertThatThrownBy(() -> storage.open("issues/abc-123/photo-1.jpg")).isInstanceOf(UncheckedIOException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"../outside.jpg", "issues/../../outside.jpg", "/etc/passwd", "C:/Windows/win.ini",
            "issues\\..\\x.jpg", "Issues/UPPER.JPG", "issues/no-extension", "issues//double.jpg"})
    void rejectsKeysThatCouldEscapeTheRoot(String key) {
        LocalDiskStorage storage = new LocalDiskStorage(root);

        assertThatThrownBy(() -> storage.put(key, new byte[] {1}, "image/jpeg"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.open(key)).isInstanceOf(IllegalArgumentException.class);
    }
}
