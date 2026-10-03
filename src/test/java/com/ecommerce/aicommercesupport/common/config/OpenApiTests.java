package com.ecommerce.aicommercesupport.common.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void servesSwaggerUiAndConfigurationWithoutLogin() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("swagger-ui")));
        mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/v3/api-docs"));
    }

    @Test
    void documentsCommerceEndpointsAuthenticationPaginationAndErrors() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Commerce Support API"))
                .andExpect(jsonPath("$.paths.length()").value(6))
                .andExpect(jsonPath("$.paths['/api/orders/{orderId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/orders/{orderId}/items'].get").exists())
                .andExpect(jsonPath("$.paths['/api/orders/{orderId}/items/{itemId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/orders/{orderId}/payment'].get").exists())
                .andExpect(jsonPath("$.paths['/api/orders/{orderId}/shipment'].get").exists())
                .andExpect(jsonPath("$.paths['/api/orders'].get.parameters[*].name")
                        .value(containsInAnyOrder("page", "size", "sort")))
                .andExpect(jsonPath("$.components.securitySchemes.basicAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.basicAuth.scheme").value("basic"))
                .andExpect(jsonPath("$.security[0].basicAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/orders/{orderId}'].get.responses['404'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ApiErrorResponse"))
                .andExpect(jsonPath("$.components.schemas.ApiErrorResponse.properties.message").exists());
    }

    @Test
    void keepsCommerceApiProtectedWhileDocumentationIsPublic() throws Exception {
        mockMvc.perform(get("/api/orders").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/orders", "/api/orders/{orderId}", "/api/orders/{orderId}/items",
            "/api/orders/{orderId}/items/{itemId}", "/api/orders/{orderId}/payment",
            "/api/orders/{orderId}/shipment"
    })
    void documentsSuccessAndErrorsForEveryEndpoint(String path) throws Exception {
        var operation = "$.paths['" + path + "'].get";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operation + ".responses['200'].content").isNotEmpty())
                .andExpect(jsonPath(operation + ".responses['401'].description").isNotEmpty())
                .andExpect(jsonPath(operation + ".responses['404'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ApiErrorResponse"));
    }
}
