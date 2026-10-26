package com.civsetu.media.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.civsetu.media.config.MediaProperties;
import com.civsetu.media.entity.MediaKind;
import com.civsetu.support.ApiTest;
import com.civsetu.support.TestFiles;

class MediaRollbackTest extends ApiTest {

    @Autowired
    MediaService media;

    @Autowired
    MediaProperties properties;

    @Autowired
    PlatformTransactionManager transactions;

    @Test
    void storedFilesAreDeletedWhenTheTransactionRollsBack() throws Exception {
        Session citizen = registerCitizen(uniqueEmail("rollback"));
        UUID issueId = UUID.fromString(reportIssue(citizen, "Rollback check", "GARBAGE", 18.5, 73.8));
        Path issueFolder = Path.of(properties.local().root(), "issues", issueId.toString());

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            media.attach(issueId, UUID.fromString(citizen.userId()), MediaKind.IMAGE,
                    List.of(new MockMultipartFile("images", "a.png", "image/png", TestFiles.png(12, 12))));
            assertThat(storedFiles(issueFolder)).hasSize(1);
            status.setRollbackOnly();
        });

        assertThat(storedFiles(issueFolder)).isEmpty();
        assertThat(media.listForIssue(issueId)).isEmpty();
    }

    private static List<Path> storedFiles(Path folder) {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
