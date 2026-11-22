package com.candidate_manager.job;

import com.candidate_manager.KeycloakTestContainer;
import com.candidate_manager.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class JobOfferIntegrationTest {

    private static final String JOB_OFFERS = "/api/job-offers";
    private static final String RECRUITER_JOB_OFFERS = "/api/recruiter/job-offers";
    private static final String PASSWORD = "Secret-pass-1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private KeycloakTestContainer keycloak;

    @Autowired
    private Clock businessClock;

    @Test
    void createsDraftOwnedByRecruiter() throws Exception {
        UUID recruiterId = UUID.randomUUID();

        String response = createDraft(recruiterId, validOfferRequest())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.validUntil").doesNotExist())
                .andExpect(jsonPath("$.employmentTime").value("FULL_TIME"))
                .andReturn().getResponse().getContentAsString();

        UUID offerId = idOf(response);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT recruiter_id, status, title, published_at FROM job_offer WHERE id = ?", offerId);
        assertThat(row.get("recruiter_id")).isEqualTo(recruiterId);
        assertThat(row.get("status")).isEqualTo("DRAFT");
        assertThat(row.get("title")).isEqualTo("Senior Java Developer");
        assertThat(row.get("published_at")).isNull();
        assertThat(salaryContractTypesOf(offerId)).containsExactlyInAnyOrder("B2B", "EMPLOYMENT");
    }

    @Test
    void returnsLocationOfCreatedDraft() throws Exception {
        MockHttpServletResponse response = createDraft(UUID.randomUUID(), validOfferRequest())
                .andExpect(status().isCreated())
                .andReturn().getResponse();

        assertThat(response.getHeader(HttpHeaders.LOCATION)).endsWith(JOB_OFFERS + "/" + idOf(response.getContentAsString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"title", "companyName", "category", "positionLevel", "workMode", "salaries",
            "description", "responsibilities", "requirements"})
    void rejectsDraftWithoutMandatoryField(String field) throws Exception {
        UUID recruiterId = UUID.randomUUID();
        Map<String, Object> request = validOfferRequest();
        request.remove(field);

        createDraft(recruiterId, request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem(field)));

        assertThat(countOffersOf(recruiterId)).isZero();
    }

    @Test
    void rejectsSalaryWithMaxAmountBelowMinAmount() throws Exception {
        Map<String, Object> request = validOfferRequest();
        request.put("salaries", List.of(salary("B2B", 30000, 20000)));

        createDraft(UUID.randomUUID(), request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("salaries[0].maxAmountNotLowerThanMinAmount")));
    }

    @Test
    void rejectsContractTypeListedTwice() throws Exception {
        Map<String, Object> request = validOfferRequest();
        request.put("salaries", List.of(salary("B2B", 20000, 25000), salary("B2B", 21000, 26000)));

        createDraft(UUID.randomUUID(), request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("eachContractTypeListedOnce")));
    }

    @Test
    void rejectsOfficeOfferWithoutLocation() throws Exception {
        Map<String, Object> request = validOfferRequest();
        request.put("workMode", "OFFICE");
        request.remove("locations");

        createDraft(UUID.randomUUID(), request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("locationGivenUnlessRemote")));
    }

    @Test
    void acceptsRemoteOfferWithoutLocation() throws Exception {
        Map<String, Object> request = validOfferRequest();
        request.put("workMode", "REMOTE");
        request.remove("locations");

        createDraft(UUID.randomUUID(), request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.locations").isEmpty());
    }

    @Test
    void rejectsValidUntilLaterThanSixtyDaysFromToday() throws Exception {
        Map<String, Object> request = validOfferRequest();
        request.put("validUntil", LocalDate.now(businessClock).plusDays(61).toString());

        createDraft(UUID.randomUUID(), request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("validUntil")));
    }

    @Test
    void rejectsUnknownCategory() throws Exception {
        Map<String, Object> request = validOfferRequest();
        request.put("category", "ASTRONAUTICS");

        createDraft(UUID.randomUUID(), request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void forbidsCandidateToCreateOffer() throws Exception {
        mockMvc.perform(post(JOB_OFFERS).with(candidate(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(validOfferRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(post(JOB_OFFERS).contentType(MediaType.APPLICATION_JSON).content(json(validOfferRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void showsDraftOnlyToItsOwner() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID offerId = idOf(createDraft(ownerId, validOfferRequest()).andReturn().getResponse().getContentAsString());

        mockMvc.perform(get(JOB_OFFERS + "/" + offerId).with(recruiter(ownerId))).andExpect(status().isOk());
        mockMvc.perform(get(JOB_OFFERS + "/" + offerId).with(recruiter(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mockMvc.perform(get(JOB_OFFERS + "/" + offerId).with(candidate(UUID.randomUUID()))).andExpect(status().isNotFound());
    }

    @Test
    void publishesDraftWithDefaultValidityOfThirtyDays() throws Exception {
        UUID recruiterId = UUID.randomUUID();
        UUID offerId = createDraftOf(recruiterId, validOfferRequest());

        mockMvc.perform(post(JOB_OFFERS + "/" + offerId + "/publish").with(recruiter(recruiterId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.validUntil").value(LocalDate.now(businessClock).plusDays(30).toString()))
                .andExpect(jsonPath("$.publishedAt").exists());

        mockMvc.perform(get(JOB_OFFERS + "/" + offerId).with(candidate(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Senior Java Developer"));
    }

    @Test
    void keepsValidUntilChosenByRecruiterWhenPublishing() throws Exception {
        UUID recruiterId = UUID.randomUUID();
        String validUntil = LocalDate.now(businessClock).plusDays(45).toString();
        Map<String, Object> request = validOfferRequest();
        request.put("validUntil", validUntil);
        UUID offerId = createDraftOf(recruiterId, request);

        mockMvc.perform(post(JOB_OFFERS + "/" + offerId + "/publish").with(recruiter(recruiterId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validUntil").value(validUntil));
    }

    @Test
    void rejectsPublishingDraftWhoseValidUntilHasPassed() throws Exception {
        UUID recruiterId = UUID.randomUUID();
        Map<String, Object> request = validOfferRequest();
        request.put("validUntil", LocalDate.now(businessClock).plusDays(10).toString());
        UUID offerId = createDraftOf(recruiterId, request);
        jdbcTemplate.update("UPDATE job_offer SET valid_until = current_date - 1 WHERE id = ?", offerId);

        mockMvc.perform(post(JOB_OFFERS + "/" + offerId + "/publish").with(recruiter(recruiterId)))
                .andExpect(status().isConflict());

        assertThat(statusOf(offerId)).isEqualTo("DRAFT");
    }

    @Test
    void rejectsPublishingAlreadyPublishedOffer() throws Exception {
        UUID recruiterId = UUID.randomUUID();
        UUID offerId = createPublishedOfferOf(recruiterId);

        mockMvc.perform(post(JOB_OFFERS + "/" + offerId + "/publish").with(recruiter(recruiterId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void updatesDraftAndReplacesItsSalaries() throws Exception {
        UUID recruiterId = UUID.randomUUID();
        UUID offerId = createDraftOf(recruiterId, validOfferRequest());
        Map<String, Object> changed = validOfferRequest();
        changed.put("title", "Lead Java Developer");
        changed.put("salaries", List.of(salary("MANDATE", 150, 180, "HOUR")));

        mockMvc.perform(put(JOB_OFFERS + "/" + offerId).with(recruiter(recruiterId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(changed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Lead Java Developer"))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        assertThat(jdbcTemplate.queryForObject("SELECT title FROM job_offer WHERE id = ?", String.class, offerId))
                .isEqualTo("Lead Java Developer");
        assertThat(salaryContractTypesOf(offerId)).containsExactly("MANDATE");
    }

    @Test
    void rejectsUpdateAndDeletionOfPublishedOffer() throws Exception {
        UUID recruiterId = UUID.randomUUID();
        UUID offerId = createPublishedOfferOf(recruiterId);
        Map<String, Object> changed = validOfferRequest();
        changed.put("title", "Changed after publication");

        mockMvc.perform(put(JOB_OFFERS + "/" + offerId).with(recruiter(recruiterId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(changed)))
                .andExpect(status().isConflict());
        mockMvc.perform(delete(JOB_OFFERS + "/" + offerId).with(recruiter(recruiterId)))
                .andExpect(status().isConflict());

        assertThat(jdbcTemplate.queryForObject("SELECT title FROM job_offer WHERE id = ?", String.class, offerId))
                .isEqualTo("Senior Java Developer");
    }

    @Test
    void deletesDraftWithItsSalariesAndLanguages() throws Exception {
        UUID recruiterId = UUID.randomUUID();
        UUID offerId = createDraftOf(recruiterId, validOfferRequest());

        mockMvc.perform(delete(JOB_OFFERS + "/" + offerId).with(recruiter(recruiterId)))
                .andExpect(status().isNoContent());

        assertThat(countOffersOf(recruiterId)).isZero();
        assertThat(salaryContractTypesOf(offerId)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job_offer_language WHERE job_offer_id = ?", Integer.class, offerId)).isZero();
    }

    @Test
    void hidesOffersOfOtherRecruitersFromChanges() throws Exception {
        UUID offerId = createDraftOf(UUID.randomUUID(), validOfferRequest());
        RequestPostProcessor otherRecruiter = recruiter(UUID.randomUUID());

        mockMvc.perform(put(JOB_OFFERS + "/" + offerId).with(otherRecruiter)
                        .contentType(MediaType.APPLICATION_JSON).content(json(validOfferRequest())))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(JOB_OFFERS + "/" + offerId + "/publish").with(otherRecruiter))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(JOB_OFFERS + "/" + offerId).with(otherRecruiter))
                .andExpect(status().isNotFound());

        assertThat(statusOf(offerId)).isEqualTo("DRAFT");
    }

    @Test
    void listsOnlyOwnOffersNewestFirstWithPaging() throws Exception {
        UUID recruiterId = UUID.randomUUID();
        UUID oldest = createDraftOf(recruiterId, validOfferRequest());
        UUID middle = createDraftOf(recruiterId, validOfferRequest());
        UUID newest = createDraftOf(recruiterId, validOfferRequest());
        createDraftOf(UUID.randomUUID(), validOfferRequest());

        mockMvc.perform(get(RECRUITER_JOB_OFFERS).param("size", "2").with(recruiter(recruiterId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offers.length()").value(2))
                .andExpect(jsonPath("$.offers[0].id").value(newest.toString()))
                .andExpect(jsonPath("$.offers[1].id").value(middle.toString()))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        mockMvc.perform(get(RECRUITER_JOB_OFFERS).param("size", "2").param("page", "1").with(recruiter(recruiterId)))
                .andExpect(jsonPath("$.offers.length()").value(1))
                .andExpect(jsonPath("$.offers[0].id").value(oldest.toString()));
    }

    @Test
    void rejectsPageSizeAboveLimit() throws Exception {
        mockMvc.perform(get(RECRUITER_JOB_OFFERS).param("size", "101").with(recruiter(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void forbidsCandidateToListRecruiterOffers() throws Exception {
        mockMvc.perform(get(RECRUITER_JOB_OFFERS).with(candidate(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignsDraftToKeycloakUserOfRecruiterToken() throws Exception {
        String username = "user" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        mockMvc.perform(post("/api/auth/register/recruiter").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username": "%s", "email": "%s@example.com", "password": "%s", "firstName": "Jan", "lastName": "Kowalski"}
                        """.formatted(username, username, PASSWORD)))
                .andExpect(status().isCreated());
        String bearer = "Bearer " + keycloak.loginThroughWebClient(username, PASSWORD);

        String response = mockMvc.perform(post(JOB_OFFERS).header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(validOfferRequest())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(jdbcTemplate.queryForObject("SELECT recruiter_id FROM job_offer WHERE id = ?", UUID.class, idOf(response)))
                .isEqualTo(keycloak.userIdOf(username));
    }

    private ResultActions createDraft(UUID recruiterId, Map<String, Object> request) throws Exception {
        return mockMvc.perform(post(JOB_OFFERS).with(recruiter(recruiterId))
                .contentType(MediaType.APPLICATION_JSON).content(json(request)));
    }

    private UUID createDraftOf(UUID recruiterId, Map<String, Object> request) throws Exception {
        return idOf(createDraft(recruiterId, request)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private UUID createPublishedOfferOf(UUID recruiterId) throws Exception {
        UUID offerId = createDraftOf(recruiterId, validOfferRequest());
        mockMvc.perform(post(JOB_OFFERS + "/" + offerId + "/publish").with(recruiter(recruiterId)))
                .andExpect(status().isOk());
        return offerId;
    }

    private UUID idOf(String responseBody) throws Exception {
        JsonNode body = objectMapper.readTree(responseBody);
        return UUID.fromString(body.get("id").asText());
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private int countOffersOf(UUID recruiterId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM job_offer WHERE recruiter_id = ?", Integer.class, recruiterId);
    }

    private String statusOf(UUID offerId) {
        return jdbcTemplate.queryForObject("SELECT status FROM job_offer WHERE id = ?", String.class, offerId);
    }

    private List<String> salaryContractTypesOf(UUID offerId) {
        return jdbcTemplate.queryForList(
                "SELECT contract_type FROM job_offer_salary WHERE job_offer_id = ?", String.class, offerId);
    }

    private static RequestPostProcessor recruiter(UUID userId) {
        return jwt().jwt(token -> token.subject(userId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_RECRUITER"));
    }

    private static RequestPostProcessor candidate(UUID userId) {
        return jwt().jwt(token -> token.subject(userId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_CANDIDATE"));
    }

    private static Map<String, Object> validOfferRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put("title", "Senior Java Developer");
        request.put("companyName", "Acme Sp. z o.o.");
        request.put("category", "IT");
        request.put("positionLevel", "SENIOR");
        request.put("workMode", "HYBRID");
        request.put("locations", List.of("Warszawa"));
        request.put("salaries", List.of(salary("B2B", 22000, 28000), salary("EMPLOYMENT", 18000, 23000)));
        request.put("description", "We are building a recruitment platform used by hundreds of companies.");
        request.put("responsibilities", List.of("Designing and developing REST APIs", "Code review"));
        request.put("requirements", List.of("Java 21", "Spring Boot", "PostgreSQL"));
        request.put("niceToHave", List.of("RabbitMQ"));
        request.put("benefits", List.of("Private medical care"));
        request.put("languages", List.of(Map.of("language", "English", "level", "B2")));
        return request;
    }

    private static Map<String, Object> salary(String contractType, int minAmount, int maxAmount) {
        return salary(contractType, minAmount, maxAmount, "MONTH");
    }

    private static Map<String, Object> salary(String contractType, int minAmount, int maxAmount, String period) {
        return Map.of("contractType", contractType, "minAmount", minAmount, "maxAmount", maxAmount,
                "currency", "PLN", "period", period);
    }
}
