package com.ecommerce.aicommercesupport;

import java.sql.SQLException;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AiCommerceSupportApplicationTests {

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoadsWithIsolatedTestDatabase() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("H2");
            assertThat(connection.getMetaData().getURL())
                    .startsWith("jdbc:h2:mem:ai_commerce_support_test");
            try (var changeLogTables = connection.getMetaData()
                    .getTables(null, null, "DATABASECHANGELOG", new String[] { "TABLE" })) {
                assertThat(changeLogTables.next()).isTrue();
            }
        }
    }

}
