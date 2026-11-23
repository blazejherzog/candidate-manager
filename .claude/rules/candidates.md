---
paths:
  - "cm-candidate/**"
  - "cm-app/src/test/java/com/candidate_manager/candidate/**"
  - "**/db/migration/*candidate*"
---

# Candidates (`cm-candidate`)

- The profile is created asynchronously from `CandidateRegisteredEvent`, so right after registration
  `GET /api/candidates/me` answers 404 for a moment and the client retries. Never create the profile on read:
  it would race with the event consumer.
- `GET` and `PUT /api/candidates/me` require the `CANDIDATE` role. The profile is found by the JWT `sub`
  (`candidate.user_id`), never by a request field. Recruiters have no access to it.
- The profile is the source of truth for the first and last name. Keycloak and its tokens keep the values from
  registration, so clients read names from the profile. `email` is read-only.
- `PUT` replaces all editable fields: text is stripped, a blank or missing optional field is cleared.
  `phoneNumber` is E.164 (`+48123456789`), checked by the request and by the database.
- Lost updates: the client sends back the `version` it read; another version answers 409. The explicit comparison
  detects saves from an outdated form, `@Version` detects overlapping transactions; both are needed.
  The update is flushed before mapping, so the response carries the new version.
- Personal data stays out of logs and `toString()`; log profile and user ids only.
