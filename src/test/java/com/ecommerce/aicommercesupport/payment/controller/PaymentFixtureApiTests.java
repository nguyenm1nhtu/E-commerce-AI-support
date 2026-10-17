package com.ecommerce.aicommercesupport.payment.controller;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.cache.type=simple")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentFixtureApiTests {

    @Autowired private MockMvc mvc;
    @Autowired private CacheManager caches;
    @Autowired private com.ecommerce.aicommercesupport.auth.service.JwtService jwt;
    private final UUID owner = UUID.randomUUID();

    @Test
    void createsDeletesReloadsAndScopesBulkDeletion() throws Exception {
        var id = createOrder(owner);
        var path = "/api/orders/" + id + "/payment";
        var foreign = UUID.randomUUID();
        var foreignPath = "/api/orders/" + createOrder(foreign) + "/payment";
        var childId = create(owner, path, "{\"status\":\"PENDING\"}");
        create(foreign, foreignPath, "{\"status\":\"PENDING\"}");
        mvc.perform(as(owner, get(path))).andExpect(status().isOk());
        mvc.perform(as(foreign, get(path))).andExpect(status().isNotFound());
        mvc.perform(as(foreign, post(path).content("{\"status\":\"PENDING\"}"))).andExpect(status().isNotFound());
        mvc.perform(as(foreign, delete(path))).andExpect(status().isNotFound());
        assertThat(caches.getCache("payment-by-order").get(UUID.fromString(id))).isNotNull();
        mvc.perform(as(owner, delete(path))).andExpect(status().isNoContent());
        assertThat(caches.getCache("payment-by-order").get(UUID.fromString(id))).isNull();
        mvc.perform(as(owner, get(path))).andExpect(status().isNotFound());
        create(owner, path, "{\"status\":\"PENDING\"}");
        mvc.perform(as(owner, get(path))).andExpect(status().isOk());
        mvc.perform(as(owner, delete("/api/payments"))).andExpect(status().isNoContent());
        mvc.perform(as(owner, get(path))).andExpect(status().isNotFound());
        mvc.perform(as(foreign, get(foreignPath))).andExpect(status().isOk());
        mvc.perform(as(owner, delete("/api/payments"))).andExpect(status().isNoContent());
    }

    @Test
    void validatesRequestsAndRejectsDuplicates() throws Exception {
        var path = "/api/orders/" + createOrder(owner) + "/payment";
        mvc.perform(as(owner, post(path).content("{}"))).andExpect(status().isBadRequest());
        create(owner, path, "{\"status\":\"PENDING\"}");
        mvc.perform(as(owner, post(path).content("{\"status\":\"PENDING\"}"))).andExpect(status().isConflict());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post("/api/orders/00000000-0000-0000-0000-000000000000/payment").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/orders/00000000-0000-0000-0000-000000000000/payment").with(csrf())).andExpect(status().isUnauthorized());
    }

    private String createOrder(UUID actor) throws Exception {
        return create(actor, "/api/orders", "{\"status\":\"PENDING\",\"orderedAt\":\"2026-10-17T00:00:00Z\",\"totalAmount\":10}");
    }

    private String create(UUID actor, String path, String body) throws Exception {
        var result = mvc.perform(as(actor, post(path).content(body))).andExpect(status().isCreated()).andReturn();
        return JsonMapper.builder().build().readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private MockHttpServletRequestBuilder as(UUID actor, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + jwt.generateAccessToken(actor,
                com.ecommerce.aicommercesupport.user.entity.UserRole.CUSTOMER).getTokenValue())
                .contentType(MediaType.APPLICATION_JSON);
    }
}
