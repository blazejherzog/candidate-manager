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
