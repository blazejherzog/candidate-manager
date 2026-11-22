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
| `cm-resume` | Candidate resumes |
| `cm-event` | Recruitment process events |
| `cm-integration` | External integrations |

Rules:
- No splitting into microservices. Module boundaries are package and Maven dependency boundaries.
- Domain modules depend only on `cm-shared`, not on each other. Cross-module communication
  goes through events or narrow application interfaces.
- Package layout within a module: `web` / `application` / `domain` / `infrastructure`
  (as in `cm-auth`).
- Domain-specific code (event listeners, handlers) belongs in its domain module, not in `cm-shared`.

## Running locally
```bash
docker compose up -d                 # Postgres :5433, Keycloak :8081, RabbitMQ :5672/:15672
export KEYCLOAK_CLIENT_SECRET=...    # cm-realm → Clients → cm-backend → Credentials (admin/admin)
./mvnw -pl cm-app -am spring-boot:run
./mvnw -DskipTests package           # executable jar: cm-app/target/cm-app-<version>.jar
./mvnw test                          # requires running Docker (Testcontainers)
./mvnw -pl <module> -am test         # tests for a single module
```
- SQL logging is off by default; enable it locally with `--logging.level.org.hibernate.SQL=debug`.
- `spring.jpa.open-in-view` is disabled: map entities to responses inside the transactional service method.
- `cm-realm` is imported from `docker-init-keycloak/cm-realm-realm.json` on Keycloak's first start
  (the file name must be `<realm>-realm.json`). An existing realm is never overwritten: after changing
  the file, delete the realm in the admin console or remove the `keycloak_pgdata` volume.
- The `cm-backend` client secret is generated on import and is not stored in the repo. Without
  `KEYCLOAK_CLIENT_SECRET` the application does not start. Keep the empty default in
  `${KEYCLOAK_CLIENT_SECRET:}`: an unresolved placeholder would be bound as literal text and pass
  `@NotBlank`, so a missing secret would only show up as 502 on the first registration.
- Register and log in (local only; `cm-web` allows the password grant for curl):
  ```bash
  curl -X POST localhost:8080/api/auth/register/candidate -H 'Content-Type: application/json' \
    -d '{"username":"anna","email":"anna@example.com","password":"Secret-pass-1","firstName":"Anna","lastName":"Nowak"}'
  curl -d grant_type=password -d client_id=cm-web -d username=anna -d password=Secret-pass-1 \
    localhost:8081/realms/cm-realm/protocol/openid-connect/token
  ```

## Database and migrations
- Schema is managed exclusively by Flyway (`src/main/resources/db/migration`).
  Hibernate only validates it: `spring.jpa.hibernate.ddl-auto: validate`.
- All migrations live in `cm-app/src/main/resources/db/migration` (one ordered history for the whole monolith).
- **Never modify an applied migration.** Every schema change = a new `V<n>__<description>.sql` file.
- Integrity constraints (UNIQUE, FK, CHECK) are defined in the database, not only in code.

## Transactions, concurrency, idempotency
- Transaction boundaries at the application service level (`@Transactional`), not in controllers.
- Resolve concurrent conflicts explicitly: optimistic locking (`@Version`),
  `SELECT ... FOR UPDATE`, or a unique constraint, depending on the case. Justify the choice.
- "Check, then write" without a lock or constraint is a bug (race condition).
- Event consumers must be idempotent. Marking an event as processed and the handler's effect
  happen in one transaction, protected by a unique `(event_id, consumer)` key.
- Publishing events after a state change: transactional outbox or publish after commit.
  Never send a message inside a transaction that may roll back.

## Tests
- JUnit 5. Integration tests run against real PostgreSQL and RabbitMQ via Testcontainers
  (no H2, no mocked database).
- Integration tests live in `cm-app/src/test` (full Spring context) and use
  `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`.
- Concurrency tests:
  - each thread runs in its own independent transaction (no `@Transactional` on the test;
    use `TransactionTemplate` or call services from separate threads),
  - synchronized start (`CountDownLatch` / `ExecutorService`),
  - assertions on the actual database state after all threads finish.
- Never claim tests pass unless they were actually run. Report the real result.

## Working rules
- Code, identifiers, and commit messages are in English.
- No comments in code (including Javadoc, YAML and SQL comments). Code must explain itself:
  precise names for classes, methods, variables, constants and tests; extract a well-named
  method or constant instead of explaining a block. The "why" behind a non-obvious decision
  goes into the commit message or this file, not into a comment.
- Plan first, then implement. Before creating or changing files, present the proposal
  and wait for approval.
- Make changes locally only and show `git diff` afterwards. **Do not commit or push** —
  the developer commits and pushes personally.
- Do not modify files unrelated to the task.
- Do not add dependencies or infrastructure without justification.
- Keep it simple. No speculative abstractions: introduce an interface only when there is
  more than one implementation or a real testing need.
- Commit message format (for reference): `[CM-<n>] Description`.

## Messaging
- Publishing: `EventPublisher.publish()` only inserts into `outbox_event` and requires an active
  transaction (`Propagation.MANDATORY`). `OutboxRelay` sends pending rows to RabbitMQ in batches
  (`FOR UPDATE SKIP LOCKED`), waits for a publisher confirm per message and marks the row as published.
  Delivery is at-least-once.
- Order: rows are sent by `sequence_number` (insertion order), which is stable within one transaction.
  Across concurrent transactions it is not commit order, so consumers must not rely on global ordering.
- Consuming: every consumer has its own queue bound to the producer's exchange and its own
  `@RabbitListener`, which wraps the effect in `IdempotentConsumer.runOnce(eventId, consumer, effect)`.
  It inserts `(event_id, consumer)` into `processed_event` (`ON CONFLICT DO NOTHING`) and runs the effect
  in the same transaction only if the row was inserted. The consumer name is the queue name.
- Naming: one topic exchange per producing module (`cm.<module>`, e.g. `cm.auth`), routing key
  `<module>.<event>` (e.g. `auth.candidate-registered`), one queue per consumer
  `<consumer-module>.<event>` (e.g. `cm-candidate.candidate-registered`), configured in `rabbitmq.queues`.
- Events exchanged between modules (contracts) live in `cm-shared` (`com.candidate_manager.shared.event`),
  because domain modules must not depend on each other. Their `toString()` must not expose personal data.
- Event contract: `DomainEvent` carries `eventId` and `occurredAt`; exchange and routing key are not part
  of the payload. Changes must be backward compatible (only add fields; consumers ignore unknown ones).
  A breaking change means a new event type.
- Queues, exchanges and bindings are a `Declarables` bean declared on the first broker connection, so the
  application starts without RabbitMQ and listeners connect once the broker is available.
- Every configured queue gets a dead-letter queue `<queue>.dlq` (via exchange `cm.dlx`); messages
  matching no binding go to `cm.unroutable.queue` (alternate exchange `cm.unroutable`).
- RabbitMQ cannot change arguments of an existing queue/exchange (`PRECONDITION_FAILED`). After changing
  them, delete the queue/exchange in the local broker once (management UI: http://localhost:15672).
  Queues removed from the configuration (e.g. `candidate.created.queue`, `candidate.exchange`) are not
  deleted automatically either; remove them there.
- Retention: `MessagingRetention` deletes published `outbox_event` rows after 7 days and `processed_event`
  rows after 30 days (`messaging.retention.*`). The `processed_event` TTL must stay far longer than any
  possible redelivery, otherwise a late duplicate is processed again.
- The relay and retention schedulers are disabled in tests (`cm-app/src/test/resources/config/application.yaml`);
  tests call `OutboxRelay.publishPendingEvents()` and `MessagingRetention.deleteExpiredRows()` directly.

## Security
- Realm roles: `CANDIDATE`, `RECRUITER` (`UserRole` in `cm-auth`). Users register themselves through
  `POST /api/auth/register/candidate` and `POST /api/auth/register/recruiter`; the role comes from the
  endpoint, never from the request body.
- Clients: `cm-backend` (confidential; API audience and service account for the Admin API, which in
  Keycloak 26.8 requires the `kc-admin-api` client scope) and `cm-web` (public; user login).
- The API accepts only tokens with audience `cm-backend`. `realm_access.roles` are mapped to `ROLE_<name>`,
  so use `@PreAuthorize("hasRole('RECRUITER')")`. All endpoints except registration require a token.
- Registration is not transactional across Keycloak calls: if assigning the role or storing the
  `CandidateRegisteredEvent` in the outbox fails, the created user is deleted again (`UserService`).
- Candidate registration publishes `CandidateRegisteredEvent`; `cm-candidate` consumes it and creates the
  candidate profile (`candidate`, linked to the Keycloak user by the unique `user_id`). Recruiter
  registration publishes no event yet.
- Realm hardening (`cm-realm-realm.json`): users cannot change their email in the Keycloak account console
  (declarative user profile, email editable by `admin` only), because `candidate.email` is a unique copy of it;
  brute force protection locks a user for 60 s (growing up to 15 min) after 5 failed logins.
  Direct access grants on `cm-web` exist only for local curl and must be disabled in production.
- Errors are returned as `ProblemDetail`. `ApiExceptionHandler` (`cm-app`) adds `errors[]` (`field`, `message`)
  to validation errors and maps optimistic locking failures to 409; module handlers map only their domain
  exceptions.

## Time
- Business dates (`LocalDate`) come from the `Clock` bean in the `app.time-zone` zone (`Europe/Warsaw`), never from
  `LocalDate.now()` without a clock. Bean Validation (`@Future`, custom constraints via `ClockProvider`) uses the
  same clock. `Instant` values do not depend on the zone.

## Job offers (`cm-job`)
- A recruiter creates an offer as `DRAFT` (`POST /api/job-offers`), may change (`PUT /api/job-offers/{id}`) or
  delete it, and publishes it (`POST /api/job-offers/{id}/publish`). A published offer can no longer be changed (409).
- The owner (`recruiter_id`) is the JWT `sub`, never a request field. A draft is visible only to its owner; anyone
  else gets 404, not 403, so its existence is not revealed. `GET /api/recruiter/job-offers` lists the caller's offers.
- `validUntil` defaults to the publication date + 30 days and may be at most 60 days ahead; a draft whose
  `validUntil` has passed cannot be published.
- Concurrent publish/change/delete of one offer is resolved by `@Version` (loser gets 409). The version is a `Long`,
  so `save()` persists a new offer (assigned UUID) instead of merging it, which would add a select and a version bump.
- Text lists are `VARCHAR(n)[]` columns (order kept, element length enforced by the database); salaries and
  languages are tables keyed by `(job_offer_id, contract_type)` and `(job_offer_id, language)`.

## Known technical debt
- Anyone can register as `RECRUITER` (no email verification or approval).
- Registration endpoints have no rate limiting.
- Outbox: a row that always fails to send (e.g. missing exchange) blocks the rows behind it; deliberate
  fail-loud choice. Alternative if needed: `attempts` + `next_attempt_at` columns with backoff.
- Outbox: one publisher confirm per message; if throughput becomes a problem, measure first, then consider
  more relay instances, batched confirms or correlated confirms.
- Dead-lettered and unroutable messages are only stored; there is no alerting or re-drive tooling.
- Job offers: a published offer past its `validUntil` stays `PUBLISHED` and visible (no expiration yet); there is
  no public listing or search of published offers; "at least one salary" is enforced only by request validation,
  because it spans two tables.