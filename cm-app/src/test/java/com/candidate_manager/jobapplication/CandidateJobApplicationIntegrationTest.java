package com.candidate_manager.jobapplication;

import com.candidate_manager.TestcontainersConfiguration;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CandidateJobApplicationIntegrationTest {

    private static final String OWN_APPLICATIONS = "/api/candidates/me/applications";
    private static final String MESSAGE = "I have five years of Java experience.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock businessClock;

    @Autowired
    private JobApplicationTestData testData;

    @Test
    void candidateAppliesForActiveOffer() throws Exception {
        UUID recruiterId = UUID.randomUUID();
        UUID offerId = testData.publishedOfferOf(recruiterId);
        UUID userId = testData.registeredCandidate();

        ResultActions response = apply(offerId, userId, "  " + MESSAGE + "  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobOfferId").value(offerId.toString()))
                .andExpect(jsonPath("$.offerTitle").value(JobApplicationTestData.OFFER_TITLE))
                .andExpect(jsonPath("$.companyName").value(JobApplicationTestData.COMPANY_NAME))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.message").value(MESSAGE));

        UUID applicationId = idOf(response);
        response.andExpect(header().string(HttpHeaders.LOCATION, endsWith("/api/applications/" + applicationId)));
        assertThat(applicationRow(applicationId))
                .containsEntry("recruiter_id", recruiterId)
                .containsEntry("candidate_id", candidateIdOf(userId))
                .containsEntry("status", "SUBMITTED");
    }

    @Test
    void acceptsApplicationWithoutMessage() throws Exception {
        UUID applicationId = idOf(apply(testData.publishedOffer(), testData.registeredCandidate(), "   ")
                .andExpect(status().isCreated()));

        assertThat(applicationRow(applicationId)).containsEntry("message", null);
    }

    @Test
    void rejectsTooLongMessage() throws Exception {
        UUID offerId = testData.publishedOffer();

        apply(offerId, testData.registeredCandidate(), "a".repeat(2001))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("message")));

        assertThat(countApplicationsFor(offerId)).isZero();
    }

    @Test
    void rejectsSecondApplicationForSameOfferEvenAfterWithdrawal() throws Exception {
        UUID offerId = testData.publishedOffer();
        UUID userId = testData.registeredCandidate();
        UUID applicationId = idOf(apply(offerId, userId, MESSAGE).andExpect(status().isCreated()));

        apply(offerId, userId, MESSAGE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("You have already applied for this job offer"));
        withdraw(applicationId, userId).andExpect(status().isOk());
        apply(offerId, userId, MESSAGE).andExpect(status().isConflict());

        assertThat(countApplicationsFor(offerId)).isEqualTo(1);
    }

    @Test
    void hidesUnknownDraftAndExpiredOffersFromApplicants() throws Exception {
        UUID userId = testData.registeredCandidate();
        UUID draftId = testData.draftOfferOf(UUID.randomUUID());
        UUID expiredOfferId = testData.publishedOffer();
        setValidUntil(expiredOfferId, LocalDate.now(businessClock).minusDays(1));

        apply(UUID.randomUUID(), userId, MESSAGE).andExpect(status().isNotFound());
        apply(draftId, userId, MESSAGE).andExpect(status().isNotFound());
        apply(expiredOfferId, userId, MESSAGE).andExpect(status().isNotFound());
    }

    @Test
    void acceptsApplicationOnLastDayOfValidity() throws Exception {
        UUID offerId = testData.publishedOffer();
        setValidUntil(offerId, LocalDate.now(businessClock));

        apply(offerId, testData.registeredCandidate(), MESSAGE).andExpect(status().isCreated());
    }

    @Test
    void rejectsApplicationUntilCandidateProfileExists() throws Exception {
        UUID userWithoutProfile = UUID.randomUUID();

        apply(testData.publishedOffer(), userWithoutProfile, MESSAGE).andExpect(status().isConflict());
        mockMvc.perform(get(OWN_APPLICATIONS).with(candidate(userWithoutProfile)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void forbidsRecruiterToUseCandidateEndpoints() throws Exception {
        UUID recruiterId = UUID.randomUUID();
        UUID offerId = testData.publishedOffer();
        UUID applicationId = idOf(apply(offerId, testData.registeredCandidate(), MESSAGE));

        mockMvc.perform(post(applicationsOf(offerId)).with(recruiter(recruiterId))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(OWN_APPLICATIONS).with(recruiter(recruiterId))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/applications/" + applicationId + "/withdraw").with(recruiter(recruiterId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsRequestsWithoutToken() throws Exception {
        mockMvc.perform(post(applicationsOf(UUID.randomUUID())).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(OWN_APPLICATIONS)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/applications/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/applications/" + UUID.randomUUID() + "/withdraw")).andExpect(status().isUnauthorized());
    }

    @Test
    void listsOnlyOwnApplicationsNewestFirstWithPaging() throws Exception {
        UUID userId = testData.registeredCandidate();
        UUID oldest = idOf(apply(testData.publishedOffer(), userId, MESSAGE));
        UUID middle = idOf(apply(testData.publishedOffer(), userId, MESSAGE));
        UUID newest = idOf(apply(testData.publishedOffer(), userId, MESSAGE));
        apply(testData.publishedOffer(), testData.registeredCandidate(), MESSAGE).andExpect(status().isCreated());

        mockMvc.perform(get(OWN_APPLICATIONS).with(candidate(userId)).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.applications.length()").value(2))
                .andExpect(jsonPath("$.applications[0].id").value(newest.toString()))
                .andExpect(jsonPath("$.applications[1].id").value(middle.toString()));
        mockMvc.perform(get(OWN_APPLICATIONS).with(candidate(userId)).param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.applications.length()").value(1))
                .andExpect(jsonPath("$.applications[0].id").value(oldest.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=10001", "page=-1", "size=0", "size=101"})
    void rejectsPagingOutsideLimits(String paging) throws Exception {
        mockMvc.perform(get(OWN_APPLICATIONS + "?" + paging).with(candidate(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem(paging.substring(0, paging.indexOf('=')))));
    }

    @Test
    void showsAndWithdrawsOnlyOwnApplication() throws Exception {
        UUID ownerId = testData.registeredCandidate();
        UUID otherCandidateId = testData.registeredCandidate();
        UUID applicationId = idOf(apply(testData.publishedOffer(), ownerId, MESSAGE));

        mockMvc.perform(get("/api/applications/" + applicationId).with(candidate(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(MESSAGE));
        mockMvc.perform(get("/api/applications/" + applicationId).with(candidate(otherCandidateId)))
                .andExpect(status().isNotFound());
        withdraw(applicationId, otherCandidateId).andExpect(status().isNotFound());

        assertThat(applicationRow(applicationId)).containsEntry("status", "SUBMITTED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUBMITTED", "IN_REVIEW", "INTERVIEW"})
    void withdrawsOpenApplication(String openStatus) throws Exception {
        UUID userId = testData.registeredCandidate();
        UUID applicationId = idOf(apply(testData.publishedOffer(), userId, MESSAGE));
        setStatus(applicationId, openStatus);

        withdraw(applicationId, userId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WITHDRAWN"));

        assertThat(applicationRow(applicationId)).containsEntry("status", "WITHDRAWN");
    }

    @ParameterizedTest
    @ValueSource(strings = {"HIRED", "REJECTED", "WITHDRAWN"})
    void rejectsWithdrawalOfFinishedApplication(String finalStatus) throws Exception {
        UUID userId = testData.registeredCandidate();
        UUID applicationId = idOf(apply(testData.publishedOffer(), userId, MESSAGE));
        setStatus(applicationId, finalStatus);

        withdraw(applicationId, userId).andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));

        assertThat(applicationRow(applicationId)).containsEntry("status", finalStatus);
    }

    @Test
    void withdrawsApplicationForExpiredOffer() throws Exception {
        UUID userId = testData.registeredCandidate();
        UUID offerId = testData.publishedOffer();
        UUID applicationId = idOf(apply(offerId, userId, MESSAGE));
        setValidUntil(offerId, LocalDate.now(businessClock).minusDays(1));

        withdraw(applicationId, userId).andExpect(status().isOk());
    }

    private ResultActions apply(UUID offerId, UUID userId, String message) throws Exception {
        return mockMvc.perform(post(applicationsOf(offerId)).with(candidate(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("message", message))));
    }

    private ResultActions withdraw(UUID applicationId, UUID userId) throws Exception {
        return mockMvc.perform(post("/api/applications/" + applicationId + "/withdraw").with(candidate(userId)));
    }

    private UUID idOf(ResultActions response) throws Exception {
        String body = response.andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private Map<String, Object> applicationRow(UUID applicationId) {
        return jdbcTemplate.queryForMap("SELECT * FROM job_application WHERE id = ?", applicationId);
    }

    private int countApplicationsFor(UUID offerId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM job_application WHERE job_offer_id = ?", Integer.class, offerId);
    }

    private UUID candidateIdOf(UUID userId) {
        return jdbcTemplate.queryForObject("SELECT id FROM candidate WHERE user_id = ?", UUID.class, userId);
    }

    private void setStatus(UUID applicationId, String status) {
        jdbcTemplate.update("UPDATE job_application SET status = ? WHERE id = ?", status, applicationId);
    }

    private void setValidUntil(UUID offerId, LocalDate validUntil) {
        jdbcTemplate.update("UPDATE job_offer SET valid_until = ? WHERE id = ?", validUntil, offerId);
    }

    private static String applicationsOf(UUID offerId) {
        return "/api/job-offers/" + offerId + "/applications";
    }

    private static RequestPostProcessor candidate(UUID userId) {
        return jwt().jwt(token -> token.subject(userId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_CANDIDATE"));
    }

    private static RequestPostProcessor recruiter(UUID userId) {
        return jwt().jwt(token -> token.subject(userId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_RECRUITER"));
    }
}
