package com.ecommerce.aicommercesupport.common.config;

import java.util.List;

import com.ecommerce.aicommercesupport.common.dto.ApiErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI commerceOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Commerce Support API").version("v1")
                        .description("Customer order read API. Authorize with a Bearer JWT whose subject is the user UUID."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean
    public OpenApiCustomizer commerceErrorResponses() {
        return openApi -> {
            ModelConverters.getInstance().read(ApiErrorResponse.class)
                    .forEach(openApi.getComponents()::addSchemas);
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((pathName, path) -> path.readOperations().forEach(operation -> {
                if (pathName.startsWith("/api/auth/")) {
                    operation.setSecurity(List.of());
                    if (!pathName.endsWith("/csrf")) {
                        operation.addParametersItem(new io.swagger.v3.oas.models.parameters.HeaderParameter()
                                .name("X-XSRF-TOKEN").required(true)
                                .description("Masked token returned by GET /api/auth/csrf; send the CSRF cookie too")
                                .schema(new Schema<String>().type("string")));
                    }
                    operation.getResponses().addApiResponse("503", errorResponse("Authentication storage unavailable"));
                    if (pathName.endsWith("/register")) {
                        operation.getResponses().addApiResponse("409", errorResponse("Email already registered"));
                    }
                }
                operation.getResponses().addApiResponse("400", errorResponse("Invalid request"));
                operation.getResponses().addApiResponse("401", pathName.startsWith("/api/auth/")
                        ? errorResponse("Invalid credentials or refresh token")
                        : new ApiResponse().description("Authentication required (Spring Security)"));
                operation.getResponses().addApiResponse("403", errorResponse("Invalid user identity or access denied"));
                if (!pathName.startsWith("/api/auth/")) {
                    operation.getResponses().addApiResponse("404", errorResponse("Resource not found or not owned by user"));
                }
                operation.getResponses().addApiResponse("500", errorResponse("Unexpected server error"));
            }));
        };
    }

    private ApiResponse errorResponse(String description) {
        return new ApiResponse().description(description)
                .content(new Content().addMediaType(MediaType.APPLICATION_JSON_VALUE,
                        new io.swagger.v3.oas.models.media.MediaType()
                                .schema(new Schema<>().$ref("#/components/schemas/ApiErrorResponse"))));
    }
}
