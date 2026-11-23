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
- Statuses: `SUBMITTED`, `IN_REVIEW`, `INTERVIEW`, then final `HIRED`, `REJECTED`, `WITHDRAWN`. A final status never
  changes (409). Overlapping changes of one application are resolved by `@Version` (loser gets 409).
- A candidate sees only own applications; someone else's application is 404. Expiration of the offer does not block
  handling or withdrawing applications already submitted.
- No events yet: an exchange is declared only together with a queue bound to it, so an event without a consumer
  would go to a missing exchange and block the whole outbox. Add events together with their first consumer.
