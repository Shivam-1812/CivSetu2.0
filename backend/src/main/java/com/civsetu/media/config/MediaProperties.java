package com.civsetu.media.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Upload rules and storage settings ({@code civsetu.media.*}).
 */
@Validated
@ConfigurationProperties("civsetu.media")
public record MediaProperties(
        @NotBlank @Pattern(regexp = "local|s3", message = "must be local or s3") String storage,
        @NotNull DataSize maxFileSize,
        @Positive int maxImages,
        @Positive int maxVoiceNotes,
        @Positive int maxImageDimension,
        @Positive int maxImageMegapixels,
        @Valid @NotNull Local local,
        @Valid @NotNull S3 s3) {

    public record Local(@NotBlank String root) {
    }

    /** Any S3-compatible service. For Cloudflare R2 the region is {@code auto}. */
    public record S3(String endpoint, String region, String bucket, String accessKey, String secretKey) {
    }
}
