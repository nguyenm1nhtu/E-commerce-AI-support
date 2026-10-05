package com.ecommerce.aicommercesupport.auth.controller;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.ecommerce.aicommercesupport.common.config.RedisProperties;
import com.ecommerce.aicommercesupport.user.entity.UserRole;
import com.ecommerce.aicommercesupport.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.data.redis.host=127.0.0.1", "spring.data.redis.port=${redis.integration.port:6379}",
        "app.redis.namespace=auth-api-test-${random.uuid}"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "redis.integration.port", matches = "[0-9]+")
class AuthRedisIntegrationTests {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private StringRedisTemplate redis;
    @Autowired private RedisProperties properties;
    @Autowired private JwtDecoder jwt;
    private final String email = UUID.randomUUID() + "@example.com";

    @AfterEach
    void cleanOwnData() {
        users.findByEmail(email).ifPresent(users::delete);
        var ownKeys = new ArrayList<String>();
        try (var cursor = redis.scan(ScanOptions.scanOptions().match(properties.namespace() + ":auth:*").build())) {
            cursor.forEachRemaining(ownKeys::add);
        }
        if (!ownKeys.isEmpty()) {
            redis.delete(ownKeys);
        }
    }

    @Test
    void completesRegistrationRefreshLoginAndLogoutWithRealRedisAndSignedJwt() throws Exception {
        var registration = register();
        var original = registration.getResponse().getCookie("refresh_token");
        var user = users.findByEmail(email).orElseThrow();
        user.setRole(UserRole.SUPPORT_AGENT);
        users.saveAndFlush(user);
        var rotated = refresh(original).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SUPPORT_AGENT")).andReturn();
        var replacement = rotated.getResponse().getCookie("refresh_token");
        assertThat(replacement.getValue()).isNotEqualTo(original.getValue());
        assertThat(replacement.getMaxAge()).isBetween(1, 604800);
        var accessToken = json.readTree(rotated.getResponse().getContentAsString()).get("accessToken").asText();
        assertThat(jwt.decode(accessToken).getClaimAsString("role")).isEqualTo("SUPPORT_AGENT");

        mvc.perform(post("/api/auth/logout").with(csrf()).cookie(replacement))
                .andExpect(status().isNoContent()).andExpect(cookie().maxAge("refresh_token", 0));
        refresh(replacement).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + accessToken)).andExpect(status().isOk());
        var login = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "password123"))))
                .andExpect(status().isOk()).andReturn();
        refresh(login.getResponse().getCookie("refresh_token")).andExpect(status().isOk());
    }

    @Test
    void rejectsReusedTokenAndRevokesItsReplacement() throws Exception {
        var original = register().getResponse().getCookie("refresh_token");
        var replacement = refresh(original).andExpect(status().isOk()).andReturn().getResponse().getCookie("refresh_token");
        refresh(original).andExpect(status().isUnauthorized()).andExpect(cookie().maxAge("refresh_token", 0));
        refresh(replacement).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsDeletedUsersAndDuplicateCookies() throws Exception {
        var original = register().getResponse().getCookie("refresh_token");
        mvc.perform(post("/api/auth/refresh").with(csrf()).cookie(original, original))
                .andExpect(status().isUnauthorized());
        users.delete(users.findByEmail(email).orElseThrow());
        refresh(original).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsExpiredSessionWithUnauthorized() throws Exception {
        var original = register().getResponse().getCookie("refresh_token");
        try (var cursor = redis.scan(ScanOptions.scanOptions().match(properties.namespace() + ":auth:session:*").build())) {
            cursor.forEachRemaining(key -> redis.expire(key, Duration.ZERO));
        }
        refresh(original).andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentRegistrationCreatesOnlyOneAccountAndReturnsConflictToTheOther() throws Exception {
        var start = new CountDownLatch(1);
        var body = json.writeValueAsString(Map.of("email", email, "password", "password123",
                "firstName", "Minh", "lastName", "Nguyen"));
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> register = () -> {
                start.await();
                return mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body)).andReturn().getResponse().getStatus();
            };
            var first = executor.submit(register);
            var second = executor.submit(register);
            start.countDown();
            assertThat(java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(users.findByEmail(email)).isPresent();
    }

    private MvcResult register() throws Exception {
        return mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "password123",
                                "firstName", "Minh", "lastName", "Nguyen"))))
                .andExpect(status().isCreated()).andReturn();
    }

    private org.springframework.test.web.servlet.ResultActions refresh(Cookie token) throws Exception {
        return mvc.perform(post("/api/auth/refresh").with(csrf()).cookie(token));
    }
}
