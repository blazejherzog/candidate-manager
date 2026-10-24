# Candidate Manager

Application for managing job offers and candidate applications. A recruiter manages
the recruitment process (job offers → applications → process stages → decision).

## Stack
- Java 21, Spring Boot 3.5.x, Maven (wrapper: `./mvnw`)
- PostgreSQL 15, Flyway (schema migrations)
- RabbitMQ (asynchronous messaging), Keycloak (OAuth2/JWT)
- JUnit 5, Testcontainers

## Architecture: modular monolith
Single deployable artifact (`cm-app`), split into Maven modules by domain:

| Module | Responsibility |
|---|---|
| `cm-app` | Entry point, security configuration, `application.yaml` |
| `cm-shared` | Shared infrastructure: messaging (publisher, dispatcher, idempotency), Keycloak/WebClient config |
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
./mvnw -pl cm-app -am spring-boot:run
./mvnw test                          # requires running Docker (Testcontainers)
./mvnw -pl <module> -am test         # tests for a single module
```
The `cm-realm` realm in Keycloak must be configured manually (no export in the repo).

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
  happen in one transaction, protected by a unique `event_id` key.
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
- Code, identifiers, comments, and commit messages are in English.
- Plan first, then implement. Before creating or changing files, present the proposal
  and wait for approval.
- Make changes locally only and show `git diff` afterwards. **Do not commit or push** —
  the developer commits and pushes personally.
- Do not modify files unrelated to the task.
- Do not add dependencies or infrastructure without justification.
- Keep it simple. No speculative abstractions: introduce an interface only when there is
  more than one implementation or a real testing need.
- Commit message format (for reference): `[CM-<n>] Description`.

## Known technical debt
- `RabbitEventDispatcher`: idempotency without a transaction (exists → handle → mark), prone to races.
- `RabbitEventPublisher`: publishes without an outbox.
- Keycloak `client-secret` stored in `application.yaml` in the repo.
- `CandidateCreated` listener and handler live in `cm-shared` instead of a domain module.
- Listener retry has no dead-letter queue: after 3 failed attempts a message is silently dropped.
- `DynamicRabbitConfig` declares queues eagerly in `@PostConstruct`, so the application
  (and every Spring Boot test) cannot start without a running RabbitMQ broker.