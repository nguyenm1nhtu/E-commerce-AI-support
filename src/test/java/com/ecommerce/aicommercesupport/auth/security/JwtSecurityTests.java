package com.ecommerce.aicommercesupport.auth.security;

import java.util.Map;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;

import com.ecommerce.aicommercesupport.auth.service.JwtService;
import com.ecommerce.aicommercesupport.order.entity.Order;
import com.ecommerce.aicommercesupport.order.entity.OrderStatus;
import com.ecommerce.aicommercesupport.order.repository.OrderRepository;
import com.ecommerce.aicommercesupport.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(JwtSecurityTests.ProbeController.class)
class JwtSecurityTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtService tokens;
    @Autowired
    private PasswordEncoder passwords;
    @Autowired
    private OrderRepository orders;

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void authenticatesRealSignedBearerTokensAndMapsRolesWithoutSession(UserRole role) throws Exception {
        var id = UUID.randomUUID();
        var token = tokens.generateAccessToken(id, role).getTokenValue();
        var result = mvc.perform(get("/test/jwt/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(id.toString()))
                .andExpect(jsonPath("$.role").value("ROLE_" + role.name())).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
        mvc.perform(get("/test/jwt/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void usesJwtSubjectForRealOrderOwnershipChecks() throws Exception {
        var owner = UUID.randomUUID();
        var order = orders.saveAndFlush(new Order(owner, OrderStatus.CONFIRMED, Instant.now(), BigDecimal.TEN));
        try {
            var path = "/api/orders/" + order.getId() + "/items";
            var ownerToken = tokens.generateAccessToken(owner, UserRole.CUSTOMER).getTokenValue();
            var otherToken = tokens.generateAccessToken(UUID.randomUUID(), UserRole.CUSTOMER).getTokenValue();
            mvc.perform(get(path).header("Authorization", "Bearer " + ownerToken)).andExpect(status().isOk());
            mvc.perform(get(path).header("Authorization", "Bearer " + otherToken)).andExpect(status().isNotFound());
        } finally {
            orders.deleteById(order.getId());
        }
    }

    @Test
    void rejectsMalformedTokensBasicAuthAndQueryStringTokens() throws Exception {
        mvc.perform(get("/test/jwt/me").header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/test/jwt/me").with(httpBasic("user", "password")))
                .andExpect(status().isUnauthorized());
        var token = tokens.generateAccessToken(UUID.randomUUID(), UserRole.CUSTOMER).getTokenValue();
        mvc.perform(get("/test/jwt/me").param("access_token", token)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"register", "login", "refresh", "logout"})
    void keepsCsrfRequiredOnAuthRoutesEvenWithBearerHeader(String endpoint) throws Exception {
        var token = tokens.generateAccessToken(UUID.randomUUID(), UserRole.CUSTOMER).getTokenValue();
        mvc.perform(post("/api/auth/" + endpoint)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/" + endpoint).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        var expected = switch (endpoint) {
            case "register", "login" -> 400;
            case "refresh" -> 401;
            default -> 204;
        };
        mvc.perform(post("/api/auth/" + endpoint).with(csrf())).andExpect(status().is(expected));
    }

    @Test
    void passwordEncoderHashesAndVerifiesPasswords() {
        var hash = passwords.encode("test-password-only");
        assertThat(hash).startsWith("{bcrypt}").doesNotContain("test-password-only");
        assertThat(passwords.matches("test-password-only", hash)).isTrue();
        assertThat(passwords.matches("incorrect", hash)).isFalse();
    }

    @RestController
    static class ProbeController {
        @GetMapping("/test/jwt/me")
        Map<String, String> me(Authentication authentication) {
            var role = authentication.getAuthorities().stream().map(authority -> authority.getAuthority())
                    .filter(authority -> authority.startsWith("ROLE_")).findFirst().orElseThrow();
            return Map.of("userId", authentication.getName(), "role", role);
        }
    }
}
