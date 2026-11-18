ALTER TABLE processed_event
    ADD COLUMN consumer VARCHAR(255) NOT NULL DEFAULT 'legacy';

ALTER TABLE processed_event
    ALTER COLUMN consumer DROP DEFAULT;

ALTER TABLE processed_event
    DROP CONSTRAINT processed_event_pkey;

ALTER TABLE processed_event
    ADD PRIMARY KEY (event_id, consumer);

ALTER TABLE processed_event
    ALTER COLUMN processed_at TYPE TIMESTAMPTZ(6);

CREATE INDEX idx_processed_event_processed_at
    ON processed_event (processed_at);

CREATE INDEX idx_outbox_event_published_at
    ON outbox_event (published_at)
    WHERE published_at IS NOT NULL;
