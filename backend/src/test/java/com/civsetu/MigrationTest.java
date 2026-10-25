package com.civsetu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.civsetu.support.IntegrationTest;

@Transactional
class MigrationTest extends IntegrationTest {

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void allMigrationsApplyOnEmptyDatabase() {
        assertThat(flyway.info().applied()).isNotEmpty();
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void requiredExtensionsAreInstalled() {
        List<String> extensions = jdbc.queryForList("SELECT extname FROM pg_extension", String.class);
        assertThat(extensions).contains("postgis", "pg_trgm", "citext");
    }

    @Test
    void emailIsUniqueIgnoringCase() {
        jdbc.update("INSERT INTO users (name, email, role) VALUES ('Asha', 'Asha@Example.test', 'CITIZEN')");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO users (name, email, role) VALUES ('Asha 2', 'asha@example.test', 'CITIZEN')"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void authorityMustBelongToADepartment() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO users (name, email, role) VALUES ('Ravi', 'ravi@example.test', 'AUTHORITY')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unknownRoleIsRejected() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO users (name, email, role) VALUES ('Mira', 'mira@example.test', 'OFFICIAL')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
