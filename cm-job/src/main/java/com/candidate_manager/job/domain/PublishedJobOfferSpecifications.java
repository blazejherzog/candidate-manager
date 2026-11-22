package com.candidate_manager.job.domain;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

public final class PublishedJobOfferSpecifications {

    private static final String LOCATIONS_SEARCH_TEXT_FUNCTION = "job_offer_locations_search_text";
    private static final char LIKE_ESCAPE_CHARACTER = '\\';

    private PublishedJobOfferSpecifications() {
    }

    public static Specification<JobOffer> activeOffersMatching(PublishedJobOfferCriteria criteria, LocalDate today) {
        return Specification.allOf(
                activeOn(today),
                titleOrCompanyContains(criteria.titleOrCompanyFragment()),
                locationContains(criteria.locationFragment()),
                attributeIn("category", criteria.categories()),
                attributeIn("positionLevel", criteria.positionLevels()),
                attributeIn("workMode", criteria.workModes()),
                attributeIn("employmentTime", criteria.employmentTimes()),
                offersAnyContractType(criteria.contractTypes()));
    }

    private static Specification<JobOffer> activeOn(LocalDate today) {
        return (offer, query, builder) -> builder.and(
                builder.equal(offer.get("status"), JobOfferStatus.PUBLISHED),
                builder.greaterThanOrEqualTo(offer.get("validUntil"), today));
    }

    private static Specification<JobOffer> titleOrCompanyContains(String fragment) {
        if (fragment == null) {
            return null;
        }
        String pattern = containsPattern(fragment);
        return (offer, query, builder) -> builder.or(
                builder.like(builder.lower(offer.get("title")), pattern, LIKE_ESCAPE_CHARACTER),
                builder.like(builder.lower(offer.get("companyName")), pattern, LIKE_ESCAPE_CHARACTER));
    }

    private static Specification<JobOffer> locationContains(String fragment) {
        if (fragment == null) {
            return null;
        }
        String pattern = containsPattern(fragment);
        return (offer, query, builder) ->
                builder.like(locationsSearchText(offer, builder), pattern, LIKE_ESCAPE_CHARACTER);
    }

    private static Expression<String> locationsSearchText(Root<JobOffer> offer, CriteriaBuilder builder) {
        return builder.function(LOCATIONS_SEARCH_TEXT_FUNCTION, String.class, offer.get("locations"));
    }

    private static Specification<JobOffer> attributeIn(String attribute, List<?> allowedValues) {
        if (allowedValues.isEmpty()) {
            return null;
        }
        return (offer, query, builder) -> offer.get(attribute).in(allowedValues);
    }

    private static Specification<JobOffer> offersAnyContractType(List<ContractType> contractTypes) {
        if (contractTypes.isEmpty()) {
            return null;
        }
        return (offer, query, builder) -> {
            Subquery<Integer> matchingSalary = query.subquery(Integer.class);
            Root<JobOffer> sameOffer = matchingSalary.correlate(offer);
            Join<JobOffer, Salary> salary = sameOffer.join("salaries");
            matchingSalary.select(builder.literal(1)).where(salary.get("contractType").in(contractTypes));
            return builder.exists(matchingSalary);
        };
    }

    private static String containsPattern(String fragment) {
        String escaped = fragment.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
