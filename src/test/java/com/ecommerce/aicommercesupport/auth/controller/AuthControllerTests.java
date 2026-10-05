package com.ecommerce.aicommercesupport.auth.controller;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.ecommerce.aicommercesupport.auth.repository.RefreshTokenRepository;
import com.ecommerce.aicommercesupport.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTests {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwords;
    @Autowired private JwtDecoder jwt;
    @MockitoBean private RefreshTokenRepository sessions;

    @Test
    void registersCustomerHashesPasswordAndLogsInCaseInsensitively() throws Exception {
        var email = UUID.randomUUID() + "@example.com";
        try {
            var registered = mvc.perform(post("/api/auth/register").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(registration(email.toUpperCase(Locale.ROOT))))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value(email))
                    .andExpect(jsonPath("$.role").value("CUSTOMER"))
                    .andExpect(jsonPath("$.firstName").value("Minh"))
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.refreshToken").doesNotExist())
                    .andExpect(jsonPath("$.passwordHash").doesNotExist())
                    .andExpect(cookie().httpOnly("refresh_token", true))
                    .andExpect(cookie().secure("refresh_token", true))
                    .andExpect(cookie().path("refresh_token", "/api/auth"))
                    .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                    .andReturn();
            assertThat(registered.getRequest().getSession(false)).isNull();
            var user = users.findByEmail(email).orElseThrow();
            assertThat(passwords.matches("password123", user.getPasswordHash())).isTrue();
            var body = json.readTree(registered.getResponse().getContentAsString());
            assertThat(body.size()).isEqualTo(8);
            assertThat(jwt.decode(body.get("accessToken").asText()).getSubject()).isEqualTo(user.getId().toString());
            mvc.perform(get("/api/orders").header("Authorization", "Bearer " + body.get("accessToken").asText()))
                    .andExpect(status().isOk());

            mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("email", email.toUpperCase(Locale.ROOT), "password", "password123"))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(user.getId().toString()))
                    .andExpect(cookie().exists("refresh_token"));
            mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(registration(email)))
                    .andExpect(status().isConflict());
            mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("email", email, "password", "wrong-password"))))
                    .andExpect(status().isUnauthorized()).andExpect(cookie().doesNotExist("refresh_token"));
        } finally {
            users.findByEmail(email).ifPresent(users::delete);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"register", "login", "refresh", "logout"})
    void rejectsMissingCsrfOnEveryAuthMutation(String endpoint) throws Exception {
        mvc.perform(post("/api/auth/" + endpoint)).andExpect(status().isForbidden());
        verifyNoInteractions(sessions);
    }

    @Test
    void rejectsInvalidInputsAndMissingRefreshCookieWithoutUsingRedis() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "long@example.com", "password", "é".repeat(37),
                                "firstName", "Minh", "lastName", "Nguyen"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "missing@example.com", "password", "password123"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"body-tokens-are-not-accepted\"}"))
                .andExpect(status().isUnauthorized()).andExpect(cookie().maxAge("refresh_token", 0));
        verifyNoInteractions(sessions);
    }

    @Test
    void rollsBackRegistrationWhenRedisCannotCreateSession() throws Exception {
        var email = UUID.randomUUID() + "@example.com";
        doThrow(new RedisConnectionFailureException("Unavailable")).when(sessions).create(any(), any(), any());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(registration(email)))
                .andExpect(status().isServiceUnavailable()).andExpect(cookie().doesNotExist("refresh_token"));
        assertThat(users.existsByEmail(email)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"refresh", "logout"})
    void failsClosedWhenRedisIsUnavailableAndDoesNotClaimLogoutSucceeded(String endpoint) throws Exception {
        if (endpoint.equals("refresh")) {
            doThrow(new RedisConnectionFailureException("Unavailable")).when(sessions).rotate(any(), any());
        } else {
            doThrow(new RedisConnectionFailureException("Unavailable")).when(sessions).revoke(any());
        }
        mvc.perform(post("/api/auth/" + endpoint).with(csrf()).cookie(new Cookie("refresh_token", "a".repeat(43))))
                .andExpect(status().isServiceUnavailable()).andExpect(cookie().doesNotExist("refresh_token"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    private String registration(String email) {
        return json.writeValueAsString(Map.of("email", email, "password", "password123", "firstName", " Minh ",
                "lastName", "Nguyen", "role", "SUPPORT_AGENT"));
    }
}
