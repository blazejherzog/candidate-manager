CREATE TABLE outbox_event
(
    id           UUID         PRIMARY KEY,
    event_type   VARCHAR(255) NOT NULL,
    exchange     VARCHAR(255) NOT NULL,
    routing_key  VARCHAR(255) NOT NULL,
    payload      JSONB        NOT NULL,
    created_at   TIMESTAMP(6) NOT NULL,
    published_at TIMESTAMP(6)
);

CREATE INDEX idx_outbox_event_unpublished
    ON outbox_event (created_at)
    WHERE published_at IS NULL;
