---
paths:
  - "**/src/test/**"
---

# Tests

- JUnit 5. Integration tests run against real PostgreSQL and RabbitMQ via Testcontainers
  (no H2, no mocked database).
- Integration tests live in `cm-app/src/test` (full Spring context) and use
  `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`.
- Concurrency tests:
  - each thread runs in its own independent transaction (no `@Transactional` on the test;
    use `TransactionTemplate` or call services from separate threads),
  - synchronized start (`CountDownLatch` / `ExecutorService`),
  - assertions on the actual database state after all threads finish.
- The outbox relay and retention schedulers are disabled in tests (`cm-app/src/test/resources/config/application.yaml`);
  tests call `OutboxRelay.publishPendingEvents()` and `MessagingRetention.deleteExpiredRows()` directly.
- Keep the number of Spring contexts low: every distinct test configuration starts its own PostgreSQL, RabbitMQ and
  Keycloak, and too many of them slow Keycloak down until requests time out (502). Use
  `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`, with `@AutoConfigureMockMvc` by default; put shared
  test data beans into `TestcontainersConfiguration` instead of importing them per class.
- Tests sharing a context share the database and the outbox. Use unique data, and
  `OutboxRelayTestSupport.relayAllPendingEvents()` instead of one batch. The context without `@AutoConfigureMockMvc` must
  leave the outbox empty: `OutboxIntegrationTest` counts its rows.
