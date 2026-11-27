---
paths:
  - "cm-shared/src/main/java/com/candidate_manager/shared/**"
  - "**/*Listener.java"
  - "**/*Event.java"
  - "**/src/main/resources/application*.yaml"
  - "cm-app/src/test/java/com/candidate_manager/messaging/**"
---

# Messaging

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
- An exchange is declared only together with a queue bound to it. An event without any consumer would go to a missing
  exchange and block the whole outbox, so add a new producing exchange together with its first consumer.
- Every configured queue gets a dead-letter queue `<queue>.dlq` (via exchange `cm.dlx`); messages
  matching no binding go to `cm.unroutable.queue` (alternate exchange `cm.unroutable`).
- RabbitMQ cannot change arguments of an existing queue/exchange (`PRECONDITION_FAILED`). After changing
  them, delete the queue/exchange in the local broker once (management UI: http://localhost:15672).
  Queues removed from the configuration (e.g. `candidate.created.queue`, `candidate.exchange`) are not
  deleted automatically either; remove them there.
- Retention: `MessagingRetention` deletes published `outbox_event` rows after 7 days and `processed_event`
  rows after 30 days (`messaging.retention.*`). The `processed_event` TTL must stay far longer than any
  possible redelivery, otherwise a late duplicate is processed again.
