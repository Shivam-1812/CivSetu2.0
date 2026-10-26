package com.civsetu.media.config;

import java.net.URI;
import java.nio.file.Path;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import com.civsetu.media.storage.LocalDiskStorage;
import com.civsetu.media.storage.MediaStorage;
import com.civsetu.media.storage.S3MediaStorage;

/**
 * Picks the {@link MediaStorage} implementation from {@code civsetu.media.storage}.
 */
@Configuration(proxyBeanMethods = false)
class StorageConfig {

    @Bean
    @ConditionalOnProperty(name = "civsetu.media.storage", havingValue = "local", matchIfMissing = true)
    MediaStorage localMediaStorage(MediaProperties properties) {
        return new LocalDiskStorage(Path.of(properties.local().root()));
    }

    @Bean
    @ConditionalOnProperty(name = "civsetu.media.storage", havingValue = "s3")
    MediaStorage s3MediaStorage(MediaProperties properties) {
        MediaProperties.S3 s3 = properties.s3();
        requireSet(s3.endpoint(), "CIVSETU_S3_ENDPOINT");
        requireSet(s3.bucket(), "CIVSETU_S3_BUCKET");
        requireSet(s3.accessKey(), "CIVSETU_S3_ACCESS_KEY");
        requireSet(s3.secretKey(), "CIVSETU_S3_SECRET_KEY");
        return new S3MediaStorage(s3Client(s3), s3.bucket());
    }

    static S3Client s3Client(MediaProperties.S3 s3) {
        return S3Client.builder()
                .endpointOverride(URI.create(s3.endpoint()))
                .region(Region.of(StringUtils.hasText(s3.region()) ? s3.region() : "auto"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3.accessKey(), s3.secretKey())))
                // R2 and other S3-compatible services expect path-style URLs
                .forcePathStyle(true)
                // Only send/verify checksums when S3 requires them; not every compatible service supports the new defaults
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }

    private static void requireSet(String value, String variable) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("civsetu.media.storage=s3 needs " + variable + " to be set");
        }
    }
}
