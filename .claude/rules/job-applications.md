---
paths:
  - "cm-application/**"
  - "cm-shared/src/main/java/com/candidate_manager/shared/directory/**"
  - "cm-app/src/test/java/com/candidate_manager/jobapplication/**"
  - "**/db/migration/*job_application*"
---

# Job applications (`cm-application`, package `jobapplication`)

- Other modules are read through narrow interfaces in `com.candidate_manager.shared.directory`, implemented by the
  owning module: `JobOfferDirectory` (`cm-job`), `CandidateDirectory` (`cm-candidate`). The call runs in the caller's
  transaction, so the state is current; there is no local copy built from events.
- A candidate applies with `POST /api/job-offers/{jobOfferId}/applications`. Only active offers accept applications;
  an unknown, draft or expired offer is 404. Without a candidate profile (not created yet) the answer is 409.
- One application per candidate and offer, forever: `UNIQUE (job_offer_id, candidate_id)` resolves concurrent
  requests (loser gets 409). Withdrawal is final and does not allow applying again.
- `recruiter_id`, `offer_title` and `company_name` are copied from the offer. A published offer cannot be changed, so
  the copy cannot become stale. Personal data of the candidate is never copied.
- Foreign keys to `job_offer` and `candidate` are intentional: one database, no plan to split into services.
- Statuses: `SUBMITTED` → `IN_REVIEW` → `INTERVIEW` → `HIRED`. The recruiter may skip stages but not go back, and may
  reject from any open status; only the candidate sets `WITHDRAWN`. `HIRED`, `REJECTED` and `WITHDRAWN` are final (409).
  The allowed changes are defined only in `JobApplicationStatus`.
- `POST /api/applications/{id}/status` carries the `version` the recruiter saw; another version answers 409. Overlapping
  changes (e.g. withdrawal and a decision) are resolved by `@Version` (loser gets 409). Changes are flushed before
  mapping, so the response carries the new version.
- Visibility: a candidate sees only own applications, a recruiter only those for own offers; anything else is 404.
  `GET /api/applications/{id}` serves both roles: `applicant` is filled only for the recruiter.
- Contact data is read live from `cm-candidate` (one query per page). After withdrawal the recruiter sees only the
  first and last name: no email, phone number or message.
- Expiration of the offer does not block handling or withdrawing applications already submitted.
- No events yet: an exchange is declared only together with a queue bound to it, so an event without a consumer
  would go to a missing exchange and block the whole outbox. Add events together with their first consumer.
