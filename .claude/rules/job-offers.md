---
paths:
  - "cm-job/**"
  - "cm-app/src/test/java/com/candidate_manager/job/**"
  - "**/db/migration/*job_offer*"
---

# Job offers (`cm-job`)

- A recruiter creates an offer as `DRAFT` (`POST /api/job-offers`), may change (`PUT /api/job-offers/{id}`) or
  delete it, and publishes it (`POST /api/job-offers/{id}/publish`). A published offer can no longer be changed (409).
- The owner (`recruiter_id`) is the JWT `sub`, never a request field. A draft is visible only to its owner; anyone
  else gets 404, not 403, so its existence is not revealed. `GET /api/recruiter/job-offers` lists the caller's offers.
- `validUntil` defaults to the publication date + 30 days and may be at most 60 days ahead; a draft whose
  `validUntil` has passed cannot be published.
- Expiration is derived from the date, not stored: an offer is active while it is `PUBLISHED` and
  `validUntil >= today` (business clock, last day inclusive). No scheduler, so visibility changes exactly at midnight
  and nothing races with publication. An expired offer is visible only to its owner (`expired: true` in responses).
  Anything that must react to expiration (e.g. closing applications) checks the same condition.
- Public search `GET /api/job-offers` (no token) lists active offers newest published first. Filters: `keyword` (title or
  company), `location` (any location), both case-insensitive fragments of 3-100 characters with `%`/`_` matched
  literally; `category`, `positionLevel`, `workMode`, `employmentTime`, `contractType` accept several values.
  There is no salary amount filter: currencies and periods differ.
- Search performance guards: trigram GIN indexes (`pg_trgm`) on `lower(title)`, `lower(company_name)` and
  `job_offer_locations_search_text(locations)` (an `IMMUTABLE` wrapper, because `array_to_string` cannot be indexed
  directly; the query must call the same function to use the index), the minimum fragment length (trigrams need 3
  characters), page size at most 100, `@BatchSize` on salaries, and a 3 s transaction timeout on the search
  that cancels the SQL query and answers 503.
- Concurrent publish/change/delete of one offer is resolved by `@Version` (loser gets 409). The version is a `Long`,
  so `save()` persists a new offer (assigned UUID) instead of merging it, which would add a select and a version bump.
- Text lists are `VARCHAR(n)[]` columns (order kept, element length enforced by the database); salaries and
  languages are tables keyed by `(job_offer_id, contract_type)` and `(job_offer_id, language)`.
