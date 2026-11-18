package com.shuttleflow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * For hosted databases that must keep their data: loads schema.sql and seed.sql only when the
 * database is empty (no users table yet). Runs while the context starts, before the schedulers.
 * Off by default; locally spring.sql.init reloads both files on every start instead.
 */
@Component
public class DatabaseInitializer implements InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    // Only the app's own schema: H2 also has a built-in INFORMATION_SCHEMA.USERS table.
    static final String TABLE_EXISTS_SQL = "SELECT COUNT(*) FROM information_schema.tables"
            + " WHERE LOWER(table_schema) = 'public' AND LOWER(table_name) = 'users'";

    private final DataSource dataSource;
    private final boolean enabled;

    public DatabaseInitializer(DataSource dataSource, @Value("${shuttleflow.db.init-if-empty:false}") boolean enabled) {
        this.dataSource = dataSource;
        this.enabled = enabled;
    }

    @Override
    public void afterPropertiesSet() {
        if (enabled) {
            initializeIfEmpty();
        }
    }

    /** True when it loaded the schema and seed; false when the database already had them. */
    boolean initializeIfEmpty() {
        Integer tables = new JdbcTemplate(dataSource).queryForObject(TABLE_EXISTS_SQL, Integer.class);
        if (tables != null && tables > 0) {
            log.info("Database already initialized; keeping existing data");
            return false;
        }
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql"), new ClassPathResource("seed.sql"))
                .execute(dataSource);
        log.info("Empty database: loaded schema.sql and seed.sql");
        return true;
    }
}
