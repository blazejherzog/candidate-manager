---
paths:
  - "cm-event/**"
  - "cm-shared/src/main/java/com/candidate_manager/shared/event/JobApplication*.java"
  - "cm-app/src/test/java/com/candidate_manager/event/**"
  - "**/db/migration/*application_history*"
---

# Application history (`cm-event`)

- `cm-application` publishes `JobApplicationSubmittedEvent` and `JobApplicationStatusChangedEvent` (also for withdrawal)
  to `cm.application` in the transaction of the change, after the flush, so the event carries the new
  `applicationVersion`. A rejected change stores no event.
- `cm-event` consumes them from `cm-event.application-submitted` and `cm-event.application-status-changed` and stores one
  `application_history_entry` per application version. History appears about a second after the change.
- Order is `application_version`, never arrival order: two queues and concurrent consumers may deliver in any order.
  Versions are consecutive, so a missing one means a dead-lettered or not yet consumed event.
- `UNIQUE (job_application_id, application_version)` protects against a different event describing the same version;
  redelivery of the same event is skipped by `IdempotentConsumer`.
- Statuses are plain text without a `CHECK`: `cm-application` owns them and a new status must not be dead-lettered.
- `GET /api/applications/{id}/history` asks `JobApplicationDirectory` (implemented by `cm-application`) about visibility,
  so the rule lives in one place; not visible means 404, not consumed yet means an empty list. It has no paging, because
  statuses only move forward and one application has at most five entries. User ids are stored but not returned.
- Events carry identifiers and statuses only, no personal data.
