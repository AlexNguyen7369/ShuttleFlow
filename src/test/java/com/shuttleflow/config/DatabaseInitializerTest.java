package com.shuttleflow.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Hosted mode: an empty database is initialized once, and a restart keeps the data. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:initifempty;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=never",
        "shuttleflow.db.init-if-empty=true"})
class DatabaseInitializerTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DatabaseInitializer initializer;

    @Test
    void anEmptyDatabaseGetsTheSchemaAndSeedAtStartup() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM availability_slots", Integer.class)).isPositive();
    }

    @Test
    void aRestartKeepsExistingData() {
        jdbc.update("UPDATE users SET full_name = 'Kept Across Restarts' WHERE user_id = 3");

        assertThat(initializer.initializeIfEmpty()).isFalse(); // what the next start does

        assertThat(jdbc.queryForObject("SELECT full_name FROM users WHERE user_id = 3", String.class))
                .isEqualTo("Kept Across Restarts");
        jdbc.update("UPDATE users SET full_name = 'Alex Nguyen' WHERE user_id = 3");
    }
}
