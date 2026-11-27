# Candidate Manager

Application for managing job offers and candidate applications. A recruiter manages
the recruitment process (job offers → applications → process stages → decision).

## Stack
- Java 21, Spring Boot 3.5.x, Maven (wrapper: `./mvnw`)
- PostgreSQL 15, Flyway (schema migrations)
- RabbitMQ (asynchronous messaging), Keycloak 26.8 (OAuth2/JWT)
- JUnit 5, Testcontainers

## Architecture: modular monolith
Single deployable artifact (`cm-app`), split into Maven modules by domain:

| Module | Responsibility |
|---|---|
| `cm-app` | Entry point, security configuration, `application.yaml` |
| `cm-shared` | Shared infrastructure: messaging (outbox, `IdempotentConsumer`, retention), Keycloak config, business `Clock` |
| `cm-auth` | Registration and users (Keycloak Admin API) |
| `cm-candidate` | Candidates |
| `cm-job` | Job offers |
| `cm-application` | Job applications (candidate applies, recruiter decides) |
| `cm-resume` | Candidate resumes |
| `cm-event` | Recruitment process history built from events |
| `cm-integration` | External integrations |

Rules:
- No splitting into microservices. Module boundaries are package and Maven dependency boundaries.
- Domain modules depend only on `cm-shared`, not on each other. Cross-module communication
  goes through events or narrow interfaces in `com.candidate_manager.shared.directory`, implemented by the owning module.
- Package layout within a module: `web` / `application` / `domain` / `infrastructure`
  (as in `cm-auth`).
- Domain-specific code (event listeners, handlers) belongs in its domain module, not in `cm-shared`.

## Running locally
```bash
docker compose up -d                 # Postgres :5433, Keycloak :8081, RabbitMQ :5672/:15672
./mvnw -pl cm-app -am spring-boot:run   # runs with the `local` profile
./mvnw -DskipTests package           # executable jar: cm-app/target/cm-app-<version>.jar
./mvnw test                          # requires running Docker (Testcontainers)
./mvnw -pl <module> -am test         # tests for a single module
```

## Rules (`.claude/rules`)
Always loaded:
- `workflow.md`: plan first, show the diff, never commit, report real test results.
- `code-style.md`: English, no comments, meaningful names (also in the API), simplicity, business clock.
- `transactions-and-concurrency.md`: transaction boundaries, explicit conflict resolution, idempotent consumers, outbox.

Loaded when working on matching files (`paths` in each file):
- `database.md`: Flyway migrations and database constraints.
- `testing.md`: Testcontainers integration and concurrency tests.
- `messaging.md`: outbox, consumers, RabbitMQ naming and topology, event contracts, retention.
- `security-and-api.md`: roles, tokens, registration, realm hardening, `ProblemDetail` errors.
- `local-environment.md`: Keycloak realm import, local secret, SQL logging, curl login.
- `candidates.md`: `cm-candidate` profile, name ownership, lost update detection.
- `job-offers.md`: `cm-job` lifecycle, visibility, expiration, public search and its performance guards.
- `job-applications.md`: `cm-application` statuses, uniqueness, visibility, reading other modules.
- `application-history.md`: `cm-event` consumers, ordering, visibility of the process history.

A new domain module gets its own rule file scoped to its paths.

## Known technical debt
- Anyone can register as `RECRUITER` (no email verification or approval).
- Registration endpoints have no rate limiting.
- Recruiters cannot change their own first and last name (no recruiter profile yet).
- Outbox: a row that always fails to send (e.g. missing exchange) blocks the rows behind it; deliberate
  fail-loud choice. Alternative if needed: `attempts` + `next_attempt_at` columns with backoff.
- Outbox: one publisher confirm per message; if throughput becomes a problem, measure first, then consider
  more relay instances, batched confirms or correlated confirms.
- Dead-lettered and unroutable messages are only stored; there is no alerting or re-drive tooling
  (a dead-lettered application event leaves a gap in the process history).
- Job offers: "at least one salary" is enforced only by request validation, because it spans two tables.
- Job offers: expiration is only derived from the date (see `cm-job`). When a module has to react to it (notify
  applicants, close the recruitment process), add an `EXPIRED` status set by a scheduler that publishes a
  `JobOfferExpiredEvent` through the outbox; keep the date condition in queries so visibility stays exact.
- Job offers: recruiters cannot close an offer before its `validUntil` yet. When they can, applying must lock the offer
  row (e.g. `FOR SHARE` in `JobOfferDirectory`), otherwise an application may be stored after closing.
- Job applications: offers have no vacancy limit; adding one requires a lock or constraint for concurrent hiring.
