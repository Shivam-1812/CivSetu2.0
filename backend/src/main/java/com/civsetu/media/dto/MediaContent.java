package com.civsetu.media.dto;

import java.io.InputStream;

/**
 * An open file ready to stream to the client. The receiver closes the stream.
 */
public record MediaContent(InputStream stream, String contentType, long sizeBytes, String fileName) {
}
