package com.ecommerce.aicommercesupport.common.persistence;

import java.util.Locale;
import java.util.UUID;

import javax.sql.DataSource;

import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmptyDatabaseMigrationTests {

    @Test
    void migratesEmptyH2DatabaseAndCanRunAgain() throws Exception {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:migration_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        verifyMigration(dataSource, false);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "MIGRATION_TEST_URL", matches = "jdbc:postgresql:.*")
    void migratesEmptyPostgresDatabaseIncludingPgvectorAndCanRunAgain() throws Exception {
        var dataSource = new DriverManagerDataSource(System.getenv("MIGRATION_TEST_URL"),
                System.getenv("MIGRATION_TEST_USER"), System.getenv("MIGRATION_TEST_PASSWORD"));
        verifyMigration(dataSource, true);
    }

    @Test
    void addsNameColumnsWithoutLosingExistingUsers() throws Exception {
        try (var dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:user_names_" + UUID.randomUUID(), "sa", "", true)) {
            var initialMigration = new SpringLiquibase();
            initialMigration.setDataSource(dataSource);
            initialMigration.setChangeLog("classpath:db/changelog/changes/004-create-users-table.sql");
            initialMigration.setResourceLoader(new DefaultResourceLoader());
            initialMigration.afterPropertiesSet();
            var jdbc = new JdbcTemplate(dataSource);
            var userId = UUID.randomUUID();
            jdbc.update("INSERT INTO users (id, email, password_hash, role) VALUES (?, ?, ?, ?)",
                    userId, "existing@example.com", "encoded-password", "CUSTOMER");

            migrate(dataSource);

            assertThat(jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, userId))
                    .isEqualTo("existing@example.com");
            assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, userId))
                    .isEqualTo("encoded-password");
            assertThat(jdbc.queryForObject("SELECT first_name FROM users WHERE id = ?", String.class, userId)).isNull();
            assertThat(jdbc.queryForObject("SELECT last_name FROM users WHERE id = ?", String.class, userId)).isNull();
            jdbc.update("UPDATE users SET first_name = ?, last_name = ? WHERE id = ?", "Minh Tú", "Nguyễn", userId);
            migrate(dataSource);
            assertThat(jdbc.queryForObject("SELECT first_name FROM users WHERE id = ?", String.class, userId))
                    .isEqualTo("Minh Tú");
            assertThat(jdbc.queryForObject("SELECT last_name FROM users WHERE id = ?", String.class, userId))
                    .isEqualTo("Nguyễn");
        }
    }

    @Test
    void constrainsExistingTicketCategoriesWithoutChangingValidData() throws Exception {
        try (var dataSource = existingTicketDatabase("SHIPMENT")) {
            migrate(dataSource);
            var jdbc = new JdbcTemplate(dataSource);
            assertThat(jdbc.queryForObject("SELECT category FROM tickets", String.class)).isEqualTo("SHIPMENT");
            assertThatThrownBy(() -> jdbc.update("UPDATE tickets SET category = 'UNSUPPORTED'"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            migrate(dataSource);
        }
    }

    @Test
    void stopsCategoryMigrationOnUnsupportedExistingDataWithoutSilentlyReclassifyingIt() throws Exception {
        try (var dataSource = existingTicketDatabase("LEGACY_CATEGORY")) {
            assertThatThrownBy(() -> migrate(dataSource)).isInstanceOf(LiquibaseException.class);
            var jdbc = new JdbcTemplate(dataSource);
            assertThat(jdbc.queryForObject("SELECT category FROM tickets", String.class)).isEqualTo("LEGACY_CATEGORY");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM databasechangelog WHERE id = '007-constrain-ticket-category'", Long.class))
                    .isZero();
        }
    }

    private SingleConnectionDataSource existingTicketDatabase(String category) throws Exception {
        var dataSource = new SingleConnectionDataSource("jdbc:h2:mem:ticket_category_" + UUID.randomUUID(), "sa", "", true);
        migrate(dataSource, "classpath:db/changelog/changes/004-create-users-table.sql");
        migrate(dataSource, "classpath:db/changelog/changes/006-create-ticketing-tables.sql");
        var jdbc = new JdbcTemplate(dataSource);
        var userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, password_hash, role) VALUES (?, ?, ?, ?)",
                userId, "ticket@example.com", "encoded-password", "CUSTOMER");
        jdbc.update("INSERT INTO tickets (id, user_id, category, status, priority, created_at) VALUES (?, ?, ?, 'OPEN', 'NORMAL', CURRENT_TIMESTAMP)",
                UUID.randomUUID(), userId, category);
        return dataSource;
    }

    private void verifyMigration(DataSource dataSource, boolean postgres) throws Exception {
        var jdbc = new JdbcTemplate(dataSource);
        var tableQuery = "SELECT table_name FROM information_schema.tables WHERE LOWER(table_schema) = 'public'";
        assertThat(jdbc.queryForList(tableQuery, String.class))
                .as("Use a dedicated empty database for this migration test")
                .isEmpty();

        migrate(dataSource);

        assertThat(jdbc.queryForList(tableQuery, String.class).stream()
                .map(name -> name.toLowerCase(Locale.ROOT)).toList())
                .containsExactlyInAnyOrder("orders", "order_items", "payments", "shipments", "users",
                        "tickets", "ticket_messages", "databasechangelog", "databasechangeloglock");
        var expectedIds = postgres
                ? new String[] {"001-enable-pgvector", "002-create-commerce-tables", "003-constrain-shipment-carrier",
                        "004-create-users-table", "005-add-user-names", "006-create-ticketing-tables", "007-constrain-ticket-category"}
                : new String[] {"002-create-commerce-tables", "003-constrain-shipment-carrier",
                        "004-create-users-table", "005-add-user-names", "006-create-ticketing-tables", "007-constrain-ticket-category"};
        assertThat(jdbc.queryForList("SELECT id FROM databasechangelog", String.class))
                .containsExactlyInAnyOrder(expectedIds);
        for (var table : new String[] {"orders", "order_items", "payments", "shipments", "users", "tickets", "ticket_messages"}) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class)).isZero();
        }
        if (postgres) {
            assertThat(jdbc.queryForObject("SELECT extversion FROM pg_extension WHERE extname = 'vector'", String.class))
                    .isNotBlank();
        }

        migrate(dataSource);

        assertThat(jdbc.queryForList("SELECT id FROM databasechangelog", String.class))
                .containsExactlyInAnyOrder(expectedIds);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM databasechangeloglock WHERE locked = TRUE", Long.class))
                .isZero();
    }

    private void migrate(DataSource dataSource) throws Exception {
        migrate(dataSource, "classpath:db/changelog/db.changelog-master.yaml");
    }

    private void migrate(DataSource dataSource, String changeLog) throws Exception {
        var liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(changeLog);
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.afterPropertiesSet();
    }
}
