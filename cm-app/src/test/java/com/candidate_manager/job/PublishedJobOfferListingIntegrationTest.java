package com.candidate_manager.job;

import com.candidate_manager.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
@Import(TestcontainersConfiguration.class)
class PublishedJobOfferListingIntegrationTest {

    private static final String JOB_OFFERS = "/api/job-offers";
    private static final String RECRUITER_JOB_OFFERS = "/api/recruiter/job-offers";
    private static final Duration LONGER_THAN_SEARCH_TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private Clock businessClock;

    @Test
    void listsOnlyActivePublishedOffersToAnonymousUser() throws Exception {
        String marker = uniqueMarker();
        UUID recruiterId = UUID.randomUUID();
        createDraftOf(recruiterId, offerRequest(marker));
        UUID expiredYesterday = publishedOfferOf(recruiterId, offerRequest(marker));
        UUID endingToday = publishedOfferOf(recruiterId, offerRequest(marker));
        UUID active = publishedOfferOf(recruiterId, offerRequest(marker));
        setValidUntil(expiredYesterday, today().minusDays(1));
        setValidUntil(endingToday, today());

        assertThat(foundOfferIds(get(JOB_OFFERS).param("keyword", marker))).containsExactly(active, endingToday);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("filtersWithExpectedOffers")
    void filtersActiveOffers(String filters, List<String> expectedOffers) throws Exception {
        String marker = uniqueMarker();
        UUID recruiterId = UUID.randomUUID();
        Map<String, UUID> offersByName = new LinkedHashMap<>();
        offersByName.put("seniorJavaInWarsaw", publishedOfferOf(recruiterId, seniorJavaInWarsaw(marker)));
        offersByName.put("juniorRemoteHr", publishedOfferOf(recruiterId, juniorRemoteHr(marker)));
        offersByName.put("salesInCracowOffice", publishedOfferOf(recruiterId, salesInCracowOffice(marker)));

        MockHttpServletRequestBuilder search = get(JOB_OFFERS + "?" + filters).param("keyword", marker);

        assertThat(foundOfferIds(search))
                .containsExactlyInAnyOrderElementsOf(expectedOffers.stream().map(offersByName::get).toList());
    }

    static Stream<Arguments> filtersWithExpectedOffers() {
        return Stream.of(
                Arguments.of("category=IT", List.of("seniorJavaInWarsaw")),
                Arguments.of("category=IT&category=HR", List.of("seniorJavaInWarsaw", "juniorRemoteHr")),
                Arguments.of("positionLevel=JUNIOR", List.of("juniorRemoteHr")),
                Arguments.of("workMode=OFFICE", List.of("salesInCracowOffice")),
                Arguments.of("employmentTime=FULL_TIME", List.of("seniorJavaInWarsaw", "salesInCracowOffice")),
                Arguments.of("contractType=B2B", List.of("seniorJavaInWarsaw")),
                Arguments.of("contractType=EMPLOYMENT", List.of("seniorJavaInWarsaw", "salesInCracowOffice")),
                Arguments.of("location=warsz", List.of("seniorJavaInWarsaw")),
                Arguments.of("location=KRAK", List.of("salesInCracowOffice")),
                Arguments.of("category=IT&workMode=OFFICE", List.of()),
                Arguments.of("category=SALES&contractType=EMPLOYMENT&location=krak", List.of("salesInCracowOffice")));
    }

    @Test
    void matchesKeywordAgainstTitleOrCompanyIgnoringCase() throws Exception {
        String marker = uniqueMarker();
        UUID recruiterId = UUID.randomUUID();
        Map<String, Object> request = offerRequest(marker);
        request.put("title", "Platform Engineer " + marker + "title");
        UUID offerId = publishedOfferOf(recruiterId, request);

        assertThat(foundOfferIds(get(JOB_OFFERS).param("keyword", marker.toUpperCase() + "TITLE"))).containsExactly(offerId);
        assertThat(foundOfferIds(get(JOB_OFFERS).param("keyword", "acme " + marker.toUpperCase()))).containsExactly(offerId);
    }

    @Test
    void treatsWildcardCharactersInKeywordLiterally() throws Exception {
        String marker = uniqueMarker();
        UUID recruiterId = UUID.randomUUID();
        UUID percentOffer = publishedOfferOf(recruiterId, offerWithCompany("Acme " + marker + " 100%"));
        UUID underscoreOffer = publishedOfferOf(recruiterId, offerWithCompany("Acme " + marker + " a_b"));
        publishedOfferOf(recruiterId, offerWithCompany("Acme " + marker + " 1000 axb"));

        assertThat(foundOfferIds(get(JOB_OFFERS).param("keyword", marker + " 100%"))).containsExactly(percentOffer);
        assertThat(foundOfferIds(get(JOB_OFFERS).param("keyword", marker + " a_b"))).containsExactly(underscoreOffer);
    }

    @ParameterizedTest
    @ValueSource(strings = {"keyword", "location"})
    void rejectsSearchFragmentShorterThanThreeCharacters(String parameter) throws Exception {
        mockMvc.perform(get(JOB_OFFERS).param(parameter, "  Wa  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem(parameter)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"keyword", "location"})
    void rejectsSearchFragmentLongerThanOneHundredCharacters(String parameter) throws Exception {
        mockMvc.perform(get(JOB_OFFERS).param(parameter, "a".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem(parameter)));
    }

    @Test
    void rejectsUnknownFilterValue() throws Exception {
        mockMvc.perform(get(JOB_OFFERS).param("category", "ASTRONAUT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("category")));
    }

    @Test
    void rejectsPageSizeAboveLimit() throws Exception {
        mockMvc.perform(get(JOB_OFFERS).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("size")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"10001", "2147483647"})
    void rejectsPageNumberAboveLimit(String page) throws Exception {
        mockMvc.perform(get(JOB_OFFERS).param("page", page).param("size", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("page")));
    }

    @Test
    void acceptsHighestAllowedPageNumberWithLargestPageSize() throws Exception {
        assertThat(foundOfferIds(get(JOB_OFFERS).param("page", "10000").param("size", "100"))).isEmpty();
    }

    @Test
    void pagesActiveOffersNewestPublishedFirst() throws Exception {
        String marker = uniqueMarker();
        UUID recruiterId = UUID.randomUUID();
        UUID oldest = publishedOfferOf(recruiterId, offerRequest(marker));
        UUID middle = publishedOfferOf(recruiterId, offerRequest(marker));
        UUID newest = publishedOfferOf(recruiterId, offerRequest(marker));

        mockMvc.perform(get(JOB_OFFERS).param("keyword", marker).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        assertThat(foundOfferIds(get(JOB_OFFERS).param("keyword", marker).param("size", "2"))).containsExactly(newest, middle);
        assertThat(foundOfferIds(get(JOB_OFFERS).param("keyword", marker).param("size", "2").param("page", "1")))
                .containsExactly(oldest);
    }

    @Test
    void returnsPublicSummaryWithSalariesWithoutInternalFields() throws Exception {
        String marker = uniqueMarker();
        publishedOfferOf(UUID.randomUUID(), seniorJavaInWarsaw(marker));

        mockMvc.perform(get(JOB_OFFERS).param("keyword", marker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offers[0].locations[0]").value("Warszawa"))
                .andExpect(jsonPath("$.offers[0].salaries[0].contractType").value("EMPLOYMENT"))
                .andExpect(jsonPath("$.offers[0].salaries[1].contractType").value("B2B"))
                .andExpect(jsonPath("$.offers[0].publishedAt").exists())
                .andExpect(jsonPath("$.offers[0].validUntil").exists())
                .andExpect(jsonPath("$.offers[0].status").doesNotExist())
                .andExpect(jsonPath("$.offers[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$.offers[0].description").doesNotExist());
    }

    @Test
    void showsActiveOfferDetailsWithoutToken() throws Exception {
        UUID offerId = publishedOfferOf(UUID.randomUUID(), offerRequest(uniqueMarker()));
        setValidUntil(offerId, today());

        mockMvc.perform(get(JOB_OFFERS + "/" + offerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expired").value(false));
    }

    @Test
    void hidesDraftFromAnonymousUser() throws Exception {
        UUID draftId = createDraftOf(UUID.randomUUID(), offerRequest(uniqueMarker()));

        mockMvc.perform(get(JOB_OFFERS + "/" + draftId)).andExpect(status().isNotFound());
    }

    @Test
    void showsExpiredOfferOnlyToItsOwner() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID offerId = publishedOfferOf(ownerId, offerRequest(uniqueMarker()));
        setValidUntil(offerId, today().minusDays(1));

        mockMvc.perform(get(JOB_OFFERS + "/" + offerId)).andExpect(status().isNotFound());
        mockMvc.perform(get(JOB_OFFERS + "/" + offerId).with(candidate(UUID.randomUUID()))).andExpect(status().isNotFound());
        mockMvc.perform(get(JOB_OFFERS + "/" + offerId).with(recruiter(UUID.randomUUID()))).andExpect(status().isNotFound());
        mockMvc.perform(get(JOB_OFFERS + "/" + offerId).with(recruiter(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.expired").value(true));
        mockMvc.perform(get(RECRUITER_JOB_OFFERS).with(recruiter(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offers[0].id").value(offerId.toString()))
                .andExpect(jsonPath("$.offers[0].expired").value(true));
    }

    @Test
    void rejectsInvalidTokenOnPublicListing() throws Exception {
        mockMvc.perform(get(JOB_OFFERS).header("Authorization", "Bearer not-a-valid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void answersServiceUnavailableWhenSearchExceedsTimeout() throws Exception {
        CountDownLatch tableLocked = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> lockHolder = executor.submit(() -> transactionTemplate.executeWithoutResult(transaction -> {
                jdbcTemplate.execute("LOCK TABLE job_offer IN ACCESS EXCLUSIVE MODE");
                tableLocked.countDown();
                awaitQuietly(releaseLock);
            }));
            assertThat(tableLocked.await(10, TimeUnit.SECONDS)).isTrue();
            try {
                mockMvc.perform(get(JOB_OFFERS).param("keyword", uniqueMarker()))
                        .andExpect(status().isServiceUnavailable())
                        .andExpect(jsonPath("$.status").value(503));
            } finally {
                releaseLock.countDown();
                lockHolder.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private List<UUID> foundOfferIds(MockHttpServletRequestBuilder search) throws Exception {
        String response = mockMvc.perform(search)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<UUID> ids = new ArrayList<>();
        for (JsonNode offer : objectMapper.readTree(response).get("offers")) {
            ids.add(UUID.fromString(offer.get("id").asText()));
        }
        return ids;
    }

    private UUID createDraftOf(UUID recruiterId, Map<String, Object> request) throws Exception {
        String response = mockMvc.perform(post(JOB_OFFERS).with(recruiter(recruiterId))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private UUID publishedOfferOf(UUID recruiterId, Map<String, Object> request) throws Exception {
        UUID offerId = createDraftOf(recruiterId, request);
        mockMvc.perform(post(JOB_OFFERS + "/" + offerId + "/publish").with(recruiter(recruiterId)))
                .andExpect(status().isOk());
        return offerId;
    }

    private void setValidUntil(UUID offerId, LocalDate validUntil) {
        jdbcTemplate.update("UPDATE job_offer SET valid_until = ? WHERE id = ?", validUntil, offerId);
    }

    private LocalDate today() {
        return LocalDate.now(businessClock);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(LONGER_THAN_SEARCH_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static String uniqueMarker() {
        return "m" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static RequestPostProcessor recruiter(UUID userId) {
        return jwt().jwt(token -> token.subject(userId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_RECRUITER"));
    }

    private static RequestPostProcessor candidate(UUID userId) {
        return jwt().jwt(token -> token.subject(userId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_CANDIDATE"));
    }

    private static Map<String, Object> seniorJavaInWarsaw(String marker) {
        return offerRequest(marker);
    }

    private static Map<String, Object> juniorRemoteHr(String marker) {
        Map<String, Object> request = offerRequest(marker);
        request.put("category", "HR");
        request.put("positionLevel", "JUNIOR");
        request.put("workMode", "REMOTE");
        request.put("employmentTime", "PART_TIME");
        request.put("locations", List.of());
        request.put("salaries", List.of(salary("MANDATE", 6000, 8000)));
        return request;
    }

    private static Map<String, Object> salesInCracowOffice(String marker) {
        Map<String, Object> request = offerRequest(marker);
        request.put("category", "SALES");
        request.put("positionLevel", "MID");
        request.put("workMode", "OFFICE");
        request.put("locations", List.of("Kraków"));
        request.put("salaries", List.of(salary("EMPLOYMENT", 9000, 12000)));
        return request;
    }

    private static Map<String, Object> offerWithCompany(String companyName) {
        Map<String, Object> request = offerRequest("");
        request.put("companyName", companyName);
        return request;
    }

    private static Map<String, Object> offerRequest(String marker) {
        Map<String, Object> request = new HashMap<>();
        request.put("title", "Senior Java Developer");
        request.put("companyName", "Acme " + marker);
        request.put("category", "IT");
        request.put("positionLevel", "SENIOR");
        request.put("workMode", "HYBRID");
        request.put("employmentTime", "FULL_TIME");
        request.put("locations", List.of("Warszawa"));
        request.put("salaries", List.of(salary("EMPLOYMENT", 18000, 23000), salary("B2B", 22000, 28000)));
        request.put("description", "We are building a recruitment platform used by hundreds of companies.");
        request.put("responsibilities", List.of("Designing and developing REST APIs"));
        request.put("requirements", List.of("Java 21"));
        return request;
    }

    private static Map<String, Object> salary(String contractType, int minAmount, int maxAmount) {
        return Map.of("contractType", contractType, "minAmount", minAmount, "maxAmount", maxAmount,
                "currency", "PLN", "period", "MONTH");
    }
}
