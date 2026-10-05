package com.ecommerce.aicommercesupport.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Separate context: csrf() test helpers replace the filter's repository and must not mask real cookie behavior.
@SpringBootTest(properties = "app.redis.namespace=csrf-http-test")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthCsrfTests {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    @Test
    void obtainsRealCsrfCookieAndMaskedHeaderWithoutCreatingHttpSession() throws Exception {
        var result = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk())
                .andExpect(cookie().httpOnly("XSRF-TOKEN", true))
                .andExpect(cookie().path("XSRF-TOKEN", "/api/auth"))
                .andExpect(cookie().secure("XSRF-TOKEN", true))
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN")).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
        var token = json.readTree(result.getResponse().getContentAsString()).get("token").asText();
        var csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
        var logout = mvc.perform(post("/api/auth/logout").cookie(csrfCookie).header("X-XSRF-TOKEN", token))
                .andExpect(status().isNoContent()).andExpect(cookie().maxAge("refresh_token", 0)).andReturn();
        assertThat(logout.getRequest().getSession(false)).isNull();
        mvc.perform(post("/api/auth/logout").header("X-XSRF-TOKEN", token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").cookie(csrfCookie).header("X-XSRF-TOKEN", "invalid"))
                .andExpect(status().isForbidden());
    }
}
