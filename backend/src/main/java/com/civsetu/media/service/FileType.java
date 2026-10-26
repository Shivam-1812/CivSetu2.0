package com.civsetu.media.service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Accepted file formats, recognised by their first bytes ("magic numbers"). The file name and
 * the Content-Type sent by the browser are never trusted.
 */
enum FileType {

    JPEG("image/jpeg", "jpg", Family.IMAGE),
    PNG("image/png", "png", Family.IMAGE),
    WEBP("image/webp", "webp", Family.IMAGE),
    WEBM_AUDIO("audio/webm", "webm", Family.AUDIO),
    OGG_AUDIO("audio/ogg", "ogg", Family.AUDIO),
    MP4_AUDIO("audio/mp4", "m4a", Family.AUDIO),
    MP3("audio/mpeg", "mp3", Family.AUDIO),
    WAV("audio/wav", "wav", Family.AUDIO),
    PDF("application/pdf", "pdf", Family.DOCUMENT);

    enum Family { IMAGE, AUDIO, DOCUMENT }

    /** Bytes needed to recognise every format above. */
    static final int HEADER_BYTES = 16;

    private final String contentType;
    private final String extension;
    private final Family family;

    FileType(String contentType, String extension, Family family) {
        this.contentType = contentType;
        this.extension = extension;
        this.family = family;
    }

    String contentType() {
        return contentType;
    }

    String extension() {
        return extension;
    }

    Family family() {
        return family;
    }

    static Optional<FileType> detect(byte[] head) {
        if (startsWith(head, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (startsWith(head, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        if (ascii(head, 0, "RIFF") && ascii(head, 8, "WEBP")) {
            return Optional.of(WEBP);
        }
        if (ascii(head, 0, "RIFF") && ascii(head, 8, "WAVE")) {
            return Optional.of(WAV);
        }
        if (startsWith(head, 0, 0x1A, 0x45, 0xDF, 0xA3)) {
            return Optional.of(WEBM_AUDIO);
        }
        if (ascii(head, 0, "OggS")) {
            return Optional.of(OGG_AUDIO);
        }
        // MP4 container; HEIC/AVIF photos use the same container, so their brands are excluded
        if (ascii(head, 4, "ftyp") && !isImageBrand(head)) {
            return Optional.of(MP4_AUDIO);
        }
        if (ascii(head, 0, "ID3") || (head.length > 1 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xE0) == 0xE0)) {
            return Optional.of(MP3);
        }
        if (ascii(head, 0, "%PDF-")) {
            return Optional.of(PDF);
        }
        return Optional.empty();
    }

    private static boolean isImageBrand(byte[] head) {
        for (String brand : new String[] {"heic", "heix", "hevc", "mif1", "msf1", "avif"}) {
            if (ascii(head, 8, brand)) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWith(byte[] data, int offset, int... expected) {
        if (data.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((data[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean ascii(byte[] data, int offset, String text) {
        byte[] expected = text.getBytes(StandardCharsets.US_ASCII);
        if (data.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (data[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
