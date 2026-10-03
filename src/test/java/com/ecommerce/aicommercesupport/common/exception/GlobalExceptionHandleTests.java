package com.ecommerce.aicommercesupport.common.exception;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandleTests {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ErrorTestController())
                .setControllerAdvice(new GlobalExceptionHandle()).build();
    }

    @Test
    void returnsStandardNotFoundResponse() throws Exception {
        mockMvc.perform(get("/test/errors/missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Order not found"))
                .andExpect(jsonPath("$.path").value("/test/errors/missing"))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void returnsFieldValidationErrors() throws Exception {
        mockMvc.perform(post("/test/errors/body").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name").isNotEmpty())
                .andExpect(jsonPath("$.errors.quantity").isNotEmpty());
    }

    @Test
    void returnsSafeMessageForMalformedBody() throws Exception {
        mockMvc.perform(post("/test/errors/body").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"))
                .andExpect(jsonPath("$.path").value("/test/errors/body"));
    }

    @Test
    void returnsSafeMessageForInvalidUuid() throws Exception {
        mockMvc.perform(get("/test/errors/id/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter: id"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void preservesResponseStatusAndReason() throws Exception {
        mockMvc.perform(get("/test/errors/status"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Order already processed"));
    }

    @Test
    void preservesMethodNotAllowedHeaderAndUsesStandardBody() throws Exception {
        mockMvc.perform(post("/test/errors/missing"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"));
    }

    @Test
    void hidesUnexpectedExceptionDetails() throws Exception {
        mockMvc.perform(get("/test/errors/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(content().string(not(containsString("internal-database-detail"))));
    }

    @Test
    void hidesServerErrorResponseStatusReason() throws Exception {
        mockMvc.perform(get("/test/errors/server-status"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(content().string(not(containsString("internal-database-detail"))));
    }

    @Test
    void preservesSecurityErrorsThrownInsideController() throws Exception {
        mockMvc.perform(get("/test/errors/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));
        mockMvc.perform(get("/test/errors/unauthorized"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @RestController
    @RequestMapping("/test/errors")
    static class ErrorTestController {

        @GetMapping("/missing")
        public void missing() {
            throw new ResourceNotFoundException("Order not found");
        }

        @PostMapping("/body")
        public ValidatedRequest body(@Valid @RequestBody ValidatedRequest request) {
            return request;
        }

        @GetMapping("/id/{id}")
        public UUID id(@PathVariable UUID id) {
            return id;
        }

        @GetMapping("/status")
        public void responseStatus() {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order already processed");
        }

        @GetMapping("/unexpected")
        public void unexpected() {
            throw new IllegalStateException("internal-database-detail");
        }

        @GetMapping("/server-status")
        public void serverStatus() {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "internal-database-detail");
        }

        @GetMapping("/forbidden")
        public void forbidden() {
            throw new AccessDeniedException("internal policy detail");
        }

        @GetMapping("/unauthorized")
        public void unauthorized() {
            throw new BadCredentialsException("internal identity detail");
        }
    }

    record ValidatedRequest(@NotBlank String name, @Positive int quantity) {
    }
}
