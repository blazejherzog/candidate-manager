---
paths:
  - "**/web/**/*.java"
  - "cm-app/src/main/java/com/candidate_manager/security/**"
  - "cm-auth/**"
  - "cm-candidate/**"
  - "docker-init-keycloak/**"
  - "cm-app/src/test/java/com/candidate_manager/auth/**"
---

# Security and API

- Realm roles: `CANDIDATE`, `RECRUITER` (`UserRole` in `cm-auth`). Users register themselves through
  `POST /api/auth/register/candidate` and `POST /api/auth/register/recruiter`; the role comes from the
  endpoint, never from the request body.
- Clients: `cm-backend` (confidential; API audience and service account for the Admin API, which in
  Keycloak 26.8 requires the `kc-admin-api` client scope) and `cm-web` (public; user login).
- The API accepts only tokens with audience `cm-backend`. `realm_access.roles` are mapped to `ROLE_<name>`,
  so use `@PreAuthorize("hasRole('RECRUITER')")`. All endpoints except registration and
  `GET /api/job-offers`, `GET /api/job-offers/{id}` require a token; an invalid token is rejected (401) everywhere.
- Registration is not transactional across Keycloak calls: if assigning the role or storing the
  `CandidateRegisteredEvent` in the outbox fails, the created user is deleted again (`UserService`).
- Candidate registration publishes `CandidateRegisteredEvent`; `cm-candidate` consumes it and creates the
  candidate profile (`candidate`, linked to the Keycloak user by the unique `user_id`). Recruiter
  registration publishes no event yet.
- Realm hardening (`cm-realm-realm.json`): users cannot change their email, first name or last name in the Keycloak
  account console (declarative user profile, editable by `admin` only), because `candidate.email` is a unique copy
  and the candidate profile owns the names; recruiters have no profile yet, so they cannot change their name at all;
  brute force protection locks a user for 60 s (growing up to 15 min) after 5 failed logins.
  Direct access grants on `cm-web` exist only for local curl and must be disabled in production.
- Errors are returned as `ProblemDetail`. `ApiExceptionHandler` (`cm-app`) adds `errors[]` (`field`, `message`)
  to validation errors (request body, query parameters), maps optimistic locking failures to 409 and query or
  transaction timeouts to 503; module handlers map only their domain exceptions.
