package com.ecommerce.aicommercesupport;

import java.sql.SQLException;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiCommerceSupportApplicationTests {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void homeDisplaysHelloWorldWithoutLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello World"));
    }

    @Test
    void otherEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health").accept("application/json"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void commerceRepositoriesDoNotExposeAutomaticRestEndpoints() throws Exception {
        for (var path : new String[] { "/orders", "/orderItems", "/payments", "/shipments" }) {
            mockMvc.perform(get(path))
                    .andExpect(status().isNotFound());
        }
    }

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
