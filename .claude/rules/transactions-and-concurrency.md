# Transactions, concurrency, idempotency

- Transaction boundaries at the application service level (`@Transactional`), not in controllers.
- `spring.jpa.open-in-view` is disabled: map entities to responses inside the transactional service method.
- Resolve concurrent conflicts explicitly: optimistic locking (`@Version`),
  `SELECT ... FOR UPDATE`, or a unique constraint, depending on the case. Justify the choice.
- "Check, then write" without a lock or constraint is a bug (race condition).
- Event consumers must be idempotent. Marking an event as processed and the handler's effect
  happen in one transaction, protected by a unique `(event_id, consumer)` key.
- Publishing events after a state change: transactional outbox or publish after commit.
  Never send a message inside a transaction that may roll back.
