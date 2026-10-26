package com.civsetu.media.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.civsetu.media.storage.S3MediaStorage;

/**
 * Runs the S3 storage against Adobe S3Mock, an S3-compatible test server, with the same client
 * settings used for Cloudflare R2.
 */
@Testcontainers
class S3MediaStorageTest {

    private static final int S3MOCK_PORT = 9090;

    @Container
    static final GenericContainer<?> S3 = new GenericContainer<>("adobe/s3mock:5.2.3").withExposedPorts(S3MOCK_PORT);

    @Test
    void storesReadsAndDeletesObjects() throws Exception {
        MediaProperties.S3 settings = new MediaProperties.S3(
                "http://" + S3.getHost() + ":" + S3.getMappedPort(S3MOCK_PORT), "auto", "civsetu-test", "test", "test");
        S3Client client = StorageConfig.s3Client(settings);
        client.createBucket(CreateBucketRequest.builder().bucket("civsetu-test").build());

        try (S3MediaStorage storage = new S3MediaStorage(client, "civsetu-test")) {
            storage.put("issues/abc/photo.jpg", new byte[] {7, 8, 9}, "image/jpeg");
            try (InputStream in = storage.open("issues/abc/photo.jpg")) {
                assertThat(in.readAllBytes()).containsExactly(7, 8, 9);
            }

            storage.delete("issues/abc/photo.jpg");
            assertThatThrownBy(() -> storage.open("issues/abc/photo.jpg")).isInstanceOf(NoSuchKeyException.class);
        }
    }
}
