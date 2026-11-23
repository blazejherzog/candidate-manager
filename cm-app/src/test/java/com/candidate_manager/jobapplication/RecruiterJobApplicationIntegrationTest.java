package com.candidate_manager.jobapplication;

import com.candidate_manager.TestcontainersConfiguration;
import com.candidate_manager.jobapplication.application.JobApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, JobApplicationTestData.class})
class RecruiterJobApplicationIntegrationTest {

    private static final String MESSAGE = "I have five years of Java experience.";
    private static final List<String> ALL_STATUSES =
            List.of("SUBMITTED", "IN_REVIEW", "INTERVIEW", "HIRED", "REJECTED", "WITHDRAWN");
    private static final Map<String, Set<String>> ALLOWED_STATUS_CHANGES = Map.of(
            "SUBMITTED", Set.of("IN_REVIEW", "INTERVIEW", "HIRED", "REJECTED"),
            "IN_REVIEW", Set.of("INTERVIEW", "HIRED", "REJECTED"),
            "INTERVIEW", Set.of("HIRED", "REJECTED"));

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

    @Autowired
    private JobApplicationService jobApplicationService;

    private UUID recruiterId;
    private UUID offerId;

    @BeforeEach
    void publishOffer() {
        recruiterId = UUID.randomUUID();
        offerId = testData.publishedOfferOf(recruiterId);
    }

    @Test
    void listsApplicationsForOwnOfferNewestFirstWithApplicantContact() throws Exception {
        UUID olderCandidate = testData.registeredCandidate();
        UUID newerCandidate = testData.registeredCandidate();
        UUID older = applicationOf(olderCandidate);
        UUID newer = applicationOf(newerCandidate);
        jobApplicationService.apply(testData.publishedOfferOf(recruiterId), olderCandidate, MESSAGE);

        mockMvc.perform(get(applications()).with(recruiter(recruiterId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.applications[0].id").value(newer.toString()))
                .andExpect(jsonPath("$.applications[0].applicant.email").value(newerCandidate + "@example.com"))
                .andExpect(jsonPath("$.applications[0].applicant.firstName").value("Jan"))
                .andExpect(jsonPath("$.applications[0].version").value(0))
                .andExpect(jsonPath("$.applications[1].id").value(older.toString()))
                .andExpect(jsonPath("$.applications[1].applicant.email").value(olderCandidate + "@example.com"));
        mockMvc.perform(get(applications()).with(recruiter(recruiterId)).param("size", "1").param("page", "1"))
                .andExpect(jsonPath("$.applications.length()").value(1))
                .andExpect(jsonPath("$.applications[0].id").value(older.toString()));
    }

    @Test
    void filtersReceivedApplicationsByOneOrMoreStatuses() throws Exception {
        UUID submitted = applicationOf(testData.registeredCandidate());
        UUID inReview = applicationOf(testData.registeredCandidate());
        UUID rejected = applicationOf(testData.registeredCandidate());
        setStatus(inReview, "IN_REVIEW");
        setStatus(rejected, "REJECTED");

        mockMvc.perform(get(applications()).with(recruiter(recruiterId)).param("status", "IN_REVIEW"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.applications[0].id").value(inReview.toString()));
        mockMvc.perform(get(applications()).with(recruiter(recruiterId)).param("status", "REJECTED", "SUBMITTED"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.applications[0].id").value(rejected.toString()))
                .andExpect(jsonPath("$.applications[1].id").value(submitted.toString()));
    }

    @Test
    void rejectsUnknownStatusFilter() throws Exception {
        mockMvc.perform(get(applications()).with(recruiter(recruiterId)).param("status", "ASTRONAUT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=10001", "page=-1", "size=0", "size=101"})
    void rejectsPagingOutsideLimits(String paging) throws Exception {
        mockMvc.perform(get(applications() + "?" + paging).with(recruiter(recruiterId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem(paging.substring(0, paging.indexOf('=')))));
    }

    @Test
    void hidesApplicationsOfOtherRecruitersOffers() throws Exception {
        UUID applicationId = applicationOf(testData.registeredCandidate());
        UUID otherRecruiter = UUID.randomUUID();

        mockMvc.perform(get(applications()).with(recruiter(otherRecruiter))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/job-offers/" + UUID.randomUUID() + "/applications").with(recruiter(recruiterId)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(application(applicationId)).with(recruiter(otherRecruiter))).andExpect(status().isNotFound());
        changeStatus(applicationId, otherRecruiter, "IN_REVIEW", 0).andExpect(status().isNotFound());

        assertThat(statusOf(applicationId)).isEqualTo("SUBMITTED");
    }

    @Test
    void forbidsCandidateToUseRecruiterEndpoints() throws Exception {
        UUID userId = testData.registeredCandidate();
        UUID applicationId = applicationOf(userId);

        mockMvc.perform(get(applications()).with(candidate(userId))).andExpect(status().isForbidden());
        mockMvc.perform(post(application(applicationId) + "/status").with(candidate(userId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "HIRED", "version", 0))))
                .andExpect(status().isForbidden());

        assertThat(statusOf(applicationId)).isEqualTo("SUBMITTED");
    }

    @Test
    void rejectsRequestsWithoutToken() throws Exception {
        mockMvc.perform(get(applications())).andExpect(status().isUnauthorized());
        mockMvc.perform(post(application(UUID.randomUUID()) + "/status")
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "HIRED", "version", 0))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void showsMessageAndCurrentContactToRecruiterOnly() throws Exception {
        UUID userId = testData.registeredCandidate();
        UUID applicationId = applicationOf(userId);
        jdbcTemplate.update("UPDATE candidate SET phone_number = '+48123456789' WHERE user_id = ?", userId);

        mockMvc.perform(get(application(applicationId)).with(recruiter(recruiterId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(MESSAGE))
                .andExpect(jsonPath("$.applicant.email").value(userId + "@example.com"))
                .andExpect(jsonPath("$.applicant.phoneNumber").value("+48123456789"));
        mockMvc.perform(get(application(applicationId)).with(candidate(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(MESSAGE))
                .andExpect(jsonPath("$.applicant").isEmpty());
    }

    @Test
    void hidesContactAndMessageAfterWithdrawal() throws Exception {
        UUID userId = testData.registeredCandidate();
        UUID applicationId = applicationOf(userId);
        jdbcTemplate.update("UPDATE candidate SET phone_number = '+48123456789' WHERE user_id = ?", userId);
        jobApplicationService.withdraw(applicationId, userId);

        mockMvc.perform(get(application(applicationId)).with(recruiter(recruiterId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.message").isEmpty())
                .andExpect(jsonPath("$.applicant.firstName").value("Jan"))
                .andExpect(jsonPath("$.applicant.lastName").value("Kowalski"))
                .andExpect(jsonPath("$.applicant.email").isEmpty())
                .andExpect(jsonPath("$.applicant.phoneNumber").isEmpty());
        mockMvc.perform(get(applications()).with(recruiter(recruiterId)))
                .andExpect(jsonPath("$.applications[0].applicant.firstName").value("Jan"))
                .andExpect(jsonPath("$.applications[0].applicant.email").isEmpty())
                .andExpect(jsonPath("$.applications[0].applicant.phoneNumber").isEmpty());
        mockMvc.perform(get(application(applicationId)).with(candidate(userId)))
                .andExpect(jsonPath("$.message").value(MESSAGE));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allStatusChanges")
    void allowsOnlyDefinedStatusChanges(String currentStatus, String newStatus, boolean allowed) throws Exception {
        UUID applicationId = applicationOf(testData.registeredCandidate());
        setStatus(applicationId, currentStatus);

        ResultActions response = changeStatus(applicationId, recruiterId, newStatus, 0);

        if (allowed) {
            response.andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(newStatus))
                    .andExpect(jsonPath("$.version").value(1));
        } else {
            response.andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        }
        assertThat(statusOf(applicationId)).isEqualTo(allowed ? newStatus : currentStatus);
    }

    static Stream<Arguments> allStatusChanges() {
        return ALL_STATUSES.stream().flatMap(currentStatus -> ALL_STATUSES.stream().map(newStatus -> Arguments.of(
                currentStatus, newStatus, ALLOWED_STATUS_CHANGES.getOrDefault(currentStatus, Set.of()).contains(newStatus))));
    }

    @Test
    void rejectsStatusChangeBasedOnOutdatedVersion() throws Exception {
        UUID applicationId = applicationOf(testData.registeredCandidate());
        changeStatus(applicationId, recruiterId, "IN_REVIEW", 0).andExpect(status().isOk());

        changeStatus(applicationId, recruiterId, "REJECTED", 0).andExpect(status().isConflict());

        assertThat(statusOf(applicationId)).isEqualTo("IN_REVIEW");
        changeStatus(applicationId, recruiterId, "REJECTED", 1).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"status", "version"})
    void rejectsStatusChangeWithoutMandatoryField(String field) throws Exception {
        UUID applicationId = applicationOf(testData.registeredCandidate());
        Map<String, Object> request = new HashMap<>(Map.of("status", "IN_REVIEW", "version", 0));
        request.remove(field);

        mockMvc.perform(post(application(applicationId) + "/status").with(recruiter(recruiterId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem(field)));
    }

    @Test
    void handlesApplicationsAfterOfferExpiration() throws Exception {
        UUID applicationId = applicationOf(testData.registeredCandidate());
        jdbcTemplate.update("UPDATE job_offer SET valid_until = ? WHERE id = ?", LocalDate.now(businessClock).minusDays(1), offerId);

        mockMvc.perform(get(applications()).with(recruiter(recruiterId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        changeStatus(applicationId, recruiterId, "HIRED", 0).andExpect(status().isOk());
    }

    private UUID applicationOf(UUID userId) {
        return jobApplicationService.apply(offerId, userId, MESSAGE).id();
    }

    private ResultActions changeStatus(UUID applicationId, UUID changingRecruiterId, String newStatus, long version) throws Exception {
        return mockMvc.perform(post(application(applicationId) + "/status").with(recruiter(changingRecruiterId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", newStatus, "version", version))));
    }

    private void setStatus(UUID applicationId, String status) {
        jdbcTemplate.update("UPDATE job_application SET status = ? WHERE id = ?", status, applicationId);
    }

    private String statusOf(UUID applicationId) {
        return jdbcTemplate.queryForObject("SELECT status FROM job_application WHERE id = ?", String.class, applicationId);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String applications() {
        return "/api/job-offers/" + offerId + "/applications";
    }

    private static String application(UUID applicationId) {
        return "/api/applications/" + applicationId;
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
