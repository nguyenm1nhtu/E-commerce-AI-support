package com.ecommerce.aicommercesupport.common.config;

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
                        .description("Customer order read API. Authorize with HTTP Basic: the username must be "
                                + "the UUID stored in orders.user_id. JWT authentication is not implemented yet."))
                .components(new Components().addSecuritySchemes("basicAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")))
                .addSecurityItem(new SecurityRequirement().addList("basicAuth"));
    }

    @Bean
    public OpenApiCustomizer commerceErrorResponses() {
        return openApi -> {
            ModelConverters.getInstance().read(ApiErrorResponse.class)
                    .forEach(openApi.getComponents()::addSchemas);
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
                operation.getResponses().addApiResponse("400", errorResponse("Invalid request"));
                operation.getResponses().addApiResponse("401",
                        new ApiResponse().description("Authentication required (Spring Security)"));
                operation.getResponses().addApiResponse("403", errorResponse("Invalid user identity or access denied"));
                operation.getResponses().addApiResponse("404", errorResponse("Resource not found or not owned by user"));
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
