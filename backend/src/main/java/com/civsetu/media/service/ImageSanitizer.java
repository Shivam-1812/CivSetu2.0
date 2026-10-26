package com.civsetu.media.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.civsetu.common.error.ApiException;
import com.civsetu.media.config.MediaProperties;

/**
 * Re-encodes uploaded photos. Writing fresh pixels drops all metadata, including EXIF GPS
 * coordinates that would reveal where the reporter took the photo. The EXIF orientation is
 * applied first so photos keep the right way up, and large photos are downscaled while decoding
 * so memory use stays bounded. PNG stays PNG (keeps transparency); JPEG and WebP become JPEG.
 */
@Component
class ImageSanitizer {

    private static final float JPEG_QUALITY = 0.85f;

    record Sanitized(byte[] bytes, FileType type) {
    }

    private final int maxDimension;
    private final long maxPixels;

    ImageSanitizer(MediaProperties properties) {
        this.maxDimension = properties.maxImageDimension();
        this.maxPixels = properties.maxImageMegapixels() * 1_000_000L;
    }

    Sanitized sanitize(byte[] original, FileType type) {
        int orientation = exifOrientation(original);
        BufferedImage decoded = decode(original);
        FileType outputType = type == FileType.PNG ? FileType.PNG : FileType.JPEG;
        BufferedImage upright = render(decoded, orientation, outputType == FileType.PNG);
        return new Sanitized(encode(upright, outputType), outputType);
    }

    private BufferedImage decode(byte[] original) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw unreadable();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                // Checked from the header, before any pixels are decoded (decompression bombs)
                if ((long) width * height > maxPixels) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_TOO_LARGE",
                            "Images may have at most " + maxPixels / 1_000_000 + " megapixels.");
                }
                ImageReadParam param = reader.getDefaultReadParam();
                int step = (Math.max(width, height) + maxDimension - 1) / maxDimension;
                if (step > 1) {
                    param.setSourceSubsampling(step, step, 0, 0);
                }
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        } catch (ApiException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // Corrupt or truncated data, or a format ImageIO cannot decode
            throw unreadable();
        }
    }

    /** Draws the image upright into a plain RGB (or ARGB) image with no metadata. */
    private static BufferedImage render(BufferedImage source, int orientation, boolean keepAlpha) {
        int w = source.getWidth();
        int h = source.getHeight();
        boolean swapsSides = orientation >= 5 && orientation <= 8;
        BufferedImage target = new BufferedImage(swapsSides ? h : w, swapsSides ? w : h,
                keepAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);

        AffineTransform transform = new AffineTransform();
        switch (orientation) {
            case 2 -> { transform.translate(w, 0); transform.scale(-1, 1); }
            case 3 -> { transform.translate(w, h); transform.rotate(Math.PI); }
            case 4 -> { transform.translate(0, h); transform.scale(1, -1); }
            case 5 -> { transform.rotate(-Math.PI / 2); transform.scale(-1, 1); }
            case 6 -> { transform.translate(h, 0); transform.rotate(Math.PI / 2); }
            case 7 -> { transform.translate(h, w); transform.rotate(Math.PI / 2); transform.scale(-1, 1); }
            case 8 -> { transform.translate(0, w); transform.rotate(3 * Math.PI / 2); }
            default -> { }
        }

        Graphics2D g = target.createGraphics();
        try {
            if (!keepAlpha) {
                // JPEG has no transparency: put transparent areas on white instead of black
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, target.getWidth(), target.getHeight());
            }
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source, transform, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private static byte[] encode(BufferedImage image, FileType type) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (type == FileType.PNG) {
                ImageIO.write(image, "png", out);
                return out.toByteArray();
            }
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            try (ImageOutputStream output = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(output);
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
                // No metadata is passed, so nothing from the original file is written
                writer.write(null, new IIOImage(image, null, null), param);
            } finally {
                writer.dispose();
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not encode image", e);
        }
    }

    /** EXIF orientation 1-8, or 1 (upright) when absent or unreadable. */
    private static int exifOrientation(byte[] original) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(original));
            ExifIFD0Directory exif = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (exif != null && exif.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return exif.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        } catch (ImageProcessingException | IOException | MetadataException e) {
            // No usable EXIF: treat as upright
        }
        return 1;
    }

    private static ApiException unreadable() {
        return new ApiException(HttpStatus.BAD_REQUEST, "UNREADABLE_IMAGE", "The image could not be read.");
    }
}
