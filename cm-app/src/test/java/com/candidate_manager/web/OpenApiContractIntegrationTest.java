package com.candidate_manager.web;

import com.candidate_manager.PostgresOnlyTestcontainersConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.rabbitmq.host=localhost", "spring.rabbitmq.port=1"})
@ActiveProfiles("local")
@AutoConfigureMockMvc
@Import(PostgresOnlyTestcontainersConfiguration.class)
class OpenApiContractIntegrationTest {

    private static final Path CONTRACT_SNAPSHOT = Path.of("..", "docs", "openapi.json");
    private static final String UPDATE_SNAPSHOT_PROPERTY = "openapi.snapshot.update";
    private static final Pattern SCHEMA_REFERENCE = Pattern.compile("#/components/schemas/(\\w+)");
    private static final Set<String> PUBLIC_OPERATIONS = Set.of(
            "POST /api/auth/register/candidate",
            "POST /api/auth/register/recruiter",
            "GET /api/job-offers",
            "GET /api/job-offers/{id}");
    private static final Set<String> ALL_OPERATIONS = Set.of(
            "POST /api/auth/register/candidate",
            "POST /api/auth/register/recruiter",
            "GET /api/candidates/me",
            "PUT /api/candidates/me",
            "GET /api/candidates/me/applications",
            "GET /api/job-offers",
            "POST /api/job-offers",
            "GET /api/job-offers/{id}",
            "PUT /api/job-offers/{id}",
            "DELETE /api/job-offers/{id}",
            "POST /api/job-offers/{id}/publish",
            "GET /api/recruiter/job-offers",
            "GET /api/job-offers/{jobOfferId}/applications",
            "POST /api/job-offers/{jobOfferId}/applications",
            "GET /api/applications/{id}",
            "POST /api/applications/{id}/withdraw",
            "POST /api/applications/{id}/status",
            "GET /api/applications/{id}/history");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void describesEveryEndpointAndWhichOnesNeedNoToken() throws Exception {
        JsonNode apiDescription = apiDescription();

        List<String> operations = new ArrayList<>();
        List<String> operationsWithoutToken = new ArrayList<>();
        apiDescription.get("paths").fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(method -> {
            String operation = method.getKey().toUpperCase() + " " + path.getKey();
            operations.add(operation);
            JsonNode security = method.getValue().get("security");
            if (security != null && security.isEmpty()) {
                operationsWithoutToken.add(operation);
            }
        }));

        assertThat(operations).containsExactlyInAnyOrderElementsOf(ALL_OPERATIONS);
        assertThat(operationsWithoutToken).containsExactlyInAnyOrderElementsOf(PUBLIC_OPERATIONS);
        assertThat(apiDescription.at("/security/0/keycloak").isArray()).isTrue();
        assertThat(apiDescription.at("/components/securitySchemes/keycloak/flows/authorizationCode/authorizationUrl").asText())
                .isEqualTo("http://localhost:8081/realms/cm-realm/protocol/openid-connect/auth");
    }

    @Test
    void describesRequestsAndResponsesAsTheApiHandlesThem() throws Exception {
        JsonNode apiDescription = apiDescription();

        List<String> searchParameters = new ArrayList<>();
        apiDescription.at("/paths/~1api~1job-offers/get/parameters").forEach(parameter -> searchParameters.add(parameter.get("name").asText()));
        assertThat(searchParameters).containsExactly(
                "keyword", "location", "category", "positionLevel", "workMode", "employmentTime", "contractType", "page", "size");
        assertThat(apiDescription.at("/paths/~1api~1job-offers/post/responses/201/content/application~1json").isMissingNode()).isFalse();
        assertThat(apiDescription.at("/paths/~1api~1job-offers~1{jobOfferId}~1applications/post/responses/201").isMissingNode()).isFalse();
        apiDescription.get("paths").forEach(path -> path.forEach(operation ->
                assertThat(operation.at("/responses/default/content/application~1problem+json/schema/$ref").asText())
                        .isEqualTo("#/components/schemas/ProblemDetail")));
        assertThat(apiDescription.at("/components/schemas/ProblemDetail/properties/errors/items/properties/field").isMissingNode()).isFalse();
    }

    @Test
    void resolvesEverySchemaReference() throws Exception {
        JsonNode apiDescription = apiDescription();
        Set<String> definedSchemas = new HashSet<>();
        apiDescription.at("/components/schemas").fieldNames().forEachRemaining(definedSchemas::add);

        Set<String> referencedSchemas = new HashSet<>();
        Matcher reference = SCHEMA_REFERENCE.matcher(apiDescription.toString());
        while (reference.find()) {
            referencedSchemas.add(reference.group(1));
        }

        assertThat(definedSchemas).containsAll(referencedSchemas);
    }

    @Test
    void matchesCommittedContractSnapshot() throws Exception {
        JsonNode apiDescription = apiDescription();
        if (Boolean.getBoolean(UPDATE_SNAPSHOT_PROPERTY)) {
            Files.writeString(CONTRACT_SNAPSHOT, objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT)
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsString(objectMapper.treeToValue(apiDescription, Object.class)) + System.lineSeparator());
        }

        assertThat(objectMapper.readTree(Files.readString(CONTRACT_SNAPSHOT)))
                .as("API changed: run the tests with -D%s=true and commit docs/openapi.json", UPDATE_SNAPSHOT_PROPERTY)
                .isEqualTo(apiDescription);
    }

    @Test
    void servesSwaggerUiInLocalProfile() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    private JsonNode apiDescription() throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
}
