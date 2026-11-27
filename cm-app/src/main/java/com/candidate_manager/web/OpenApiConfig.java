package com.candidate_manager.web;

import com.candidate_manager.security.PublicEndpoint;
import com.candidate_manager.shared.config.keycloak.KeycloakProperties;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
class OpenApiConfig {

    private static final String KEYCLOAK_SECURITY_SCHEME = "keycloak";
    private static final String PROBLEM_DETAIL_SCHEMA = "ProblemDetail";
    private static final String PROBLEM_JSON = "application/problem+json";

    @Bean
    OpenAPI candidateManagerApi(KeycloakProperties keycloakProperties) {
        String openIdConnectUrl = keycloakProperties.getUrl() + "/realms/" + keycloakProperties.getRealm() + "/protocol/openid-connect";
        OAuthFlow authorizationCodeFlow = new OAuthFlow()
                .authorizationUrl(openIdConnectUrl + "/auth")
                .tokenUrl(openIdConnectUrl + "/token");
        return new OpenAPI()
                .info(new Info().title("Candidate Manager API").version("v1"))
                .servers(List.of(new Server().url("/")))
                .components(new Components()
                        .addSecuritySchemes(KEYCLOAK_SECURITY_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .flows(new OAuthFlows().authorizationCode(authorizationCodeFlow))))
                .addSecurityItem(new SecurityRequirement().addList(KEYCLOAK_SECURITY_SCHEME));
    }

    @Bean
    OpenApiCustomizer errorsDescribedAsProblemDetail() {
        return openApi -> {
            openApi.getComponents().addSchemas(PROBLEM_DETAIL_SCHEMA, problemDetailSchema());
            openApi.getPaths().values().forEach(pathItem -> pathItem.readOperations().forEach(operation ->
                    operation.getResponses().addApiResponse("default", new ApiResponse()
                            .description("Error described as RFC 9457 problem details")
                            .content(new Content().addMediaType(PROBLEM_JSON, new MediaType()
                                    .schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_DETAIL_SCHEMA)))))));
        };
    }

    @Bean
    OpenApiCustomizer publicEndpointsWithoutToken() {
        return openApi -> PublicEndpoint.ALL.forEach(endpoint -> {
            PathItem pathItem = openApi.getPaths().get(endpoint.path());
            if (pathItem == null) {
                throw new IllegalStateException("Public endpoint %s %s is missing in the API description"
                        .formatted(endpoint.method(), endpoint.path()));
            }
            Operation operation = pathItem.readOperationsMap().get(PathItem.HttpMethod.valueOf(endpoint.method().name()));
            operation.setSecurity(List.of());
        });
    }

    private static Schema<?> problemDetailSchema() {
        Schema<?> fieldViolation = new ObjectSchema()
                .addProperty("field", new StringSchema())
                .addProperty("message", new StringSchema());
        return new ObjectSchema()
                .addProperty("type", new StringSchema())
                .addProperty("title", new StringSchema())
                .addProperty("status", new IntegerSchema())
                .addProperty("detail", new StringSchema())
                .addProperty("instance", new StringSchema())
                .addProperty("errors", new ArraySchema().items(fieldViolation));
    }
}
