package com.civsetu.support;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.imageio.ImageIO;

/**
 * Builds small, real files for upload tests.
 */
public final class TestFiles {

    private TestFiles() {
    }

    public static byte[] jpeg(int width, int height) {
        return encode(image(width, height, BufferedImage.TYPE_INT_RGB), "jpeg");
    }

    public static byte[] png(int width, int height) {
        return encode(image(width, height, BufferedImage.TYPE_INT_ARGB), "png");
    }

    /**
     * A JPEG carrying an EXIF block with an orientation flag and an ImageDescription text,
     * e.g. a fake GPS note, so tests can check that metadata is removed and rotation applied.
     */
    public static byte[] jpegWithExif(int width, int height, int orientation, String description) {
        byte[] text = (description + "\0").getBytes(StandardCharsets.US_ASCII);
        int ifdSize = 2 + 2 * 12 + 4;
        ByteBuffer tiff = ByteBuffer.allocate(8 + ifdSize + text.length).order(ByteOrder.BIG_ENDIAN);
        tiff.put((byte) 'M').put((byte) 'M').putShort((short) 42).putInt(8);
        tiff.putShort((short) 2);
        // 0x010E ImageDescription, ASCII, stored after the IFD
        tiff.putShort((short) 0x010E).putShort((short) 2).putInt(text.length).putInt(8 + ifdSize);
        // 0x0112 Orientation, SHORT, value inline
        tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
        tiff.putInt(0);
        tiff.put(text);

        byte[] exifHeader = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);
        int segmentLength = 2 + exifHeader.length + tiff.capacity();
        byte[] jpeg = jpeg(width, height);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2); // SOI
        out.write(0xFF);
        out.write(0xE1); // APP1
        out.write(segmentLength >> 8);
        out.write(segmentLength & 0xFF);
        out.writeBytes(exifHeader);
        out.writeBytes(tiff.array());
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    /** Starts with the WebM/Matroska signature, like a browser MediaRecorder voice note. */
    public static byte[] webmVoice() {
        byte[] bytes = new byte[256];
        Arrays.fill(bytes, (byte) 0x42);
        bytes[0] = 0x1A;
        bytes[1] = 0x45;
        bytes[2] = (byte) 0xDF;
        bytes[3] = (byte) 0xA3;
        return bytes;
    }

    public static BufferedImage decode(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static boolean contains(byte[] haystack, String needle) {
        byte[] target = needle.getBytes(StandardCharsets.US_ASCII);
        outer:
        for (int i = 0; i <= haystack.length - target.length; i++) {
            for (int j = 0; j < target.length; j++) {
                if (haystack[i + j] != target[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static BufferedImage image(int width, int height, int type) {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(30, 120, 200));
        g.fillRect(0, 0, width, height);
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, width / 2, height / 2);
        g.dispose();
        return image;
    }

    private static byte[] encode(BufferedImage image, String format) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, format, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
