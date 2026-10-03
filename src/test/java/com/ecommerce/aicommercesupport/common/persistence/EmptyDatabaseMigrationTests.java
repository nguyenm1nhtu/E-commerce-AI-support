package com.ecommerce.aicommercesupport.common.persistence;

import java.util.Locale;
import java.util.UUID;

import javax.sql.DataSource;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

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
                        "databasechangelog", "databasechangeloglock");
        var expectedIds = postgres
                ? new String[] {"001-enable-pgvector", "002-create-commerce-tables", "003-constrain-shipment-carrier",
                        "004-create-users-table"}
                : new String[] {"002-create-commerce-tables", "003-constrain-shipment-carrier", "004-create-users-table"};
        assertThat(jdbc.queryForList("SELECT id FROM databasechangelog", String.class))
                .containsExactlyInAnyOrder(expectedIds);
        for (var table : new String[] {"orders", "order_items", "payments", "shipments", "users"}) {
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
        var liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.afterPropertiesSet();
    }
}
