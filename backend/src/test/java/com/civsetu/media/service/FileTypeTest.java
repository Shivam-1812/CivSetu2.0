package com.civsetu.media.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.civsetu.support.TestFiles;

class FileTypeTest {

    private static byte[] ascii(String text) {
        return Arrays.copyOf(text.getBytes(StandardCharsets.ISO_8859_1), 16);
    }

    @Test
    void recognisesAcceptedFormatsByTheirFirstBytes() {
        assertThat(FileType.detect(TestFiles.jpeg(4, 4))).contains(FileType.JPEG);
        assertThat(FileType.detect(TestFiles.png(4, 4))).contains(FileType.PNG);
        assertThat(FileType.detect(ascii("RIFF\0\0\0\0WEBPVP8 "))).contains(FileType.WEBP);
        assertThat(FileType.detect(ascii("RIFF\0\0\0\0WAVEfmt "))).contains(FileType.WAV);
        assertThat(FileType.detect(TestFiles.webmVoice())).contains(FileType.WEBM_AUDIO);
        assertThat(FileType.detect(ascii("OggS\0\2"))).contains(FileType.OGG_AUDIO);
        assertThat(FileType.detect(ascii("\0\0\0 ftypM4A "))).contains(FileType.MP4_AUDIO);
        assertThat(FileType.detect(ascii("ID3\4\0"))).contains(FileType.MP3);
        assertThat(FileType.detect(ascii("%PDF-1.7"))).contains(FileType.PDF);
    }

    @Test
    void rejectsEverythingElse() {
        assertThat(FileType.detect(ascii("<html><script>"))).isEmpty();
        assertThat(FileType.detect(ascii("MZ\u0090\0"))).isEmpty(); // Windows executable
        assertThat(FileType.detect(new byte[0])).isEmpty();
        // iPhone HEIC photos share the MP4 container but are not audio
        assertThat(FileType.detect(ascii("\0\0\0\u0018ftypheic"))).isEmpty();
    }
}
