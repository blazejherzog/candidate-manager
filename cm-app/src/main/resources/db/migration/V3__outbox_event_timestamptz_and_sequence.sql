-- Existing values were written by now() in the session time zone, so converting them
-- in the same (session) time zone keeps the same instants.
ALTER TABLE outbox_event
    ALTER COLUMN created_at TYPE TIMESTAMPTZ(6),
    ALTER COLUMN published_at TYPE TIMESTAMPTZ(6);

-- now() returns the transaction start time, so events stored in one transaction share created_at.
-- The sequence gives them a stable order (insertion order) for the relay.
ALTER TABLE outbox_event
    ADD COLUMN sequence_number BIGINT GENERATED ALWAYS AS IDENTITY;

DROP INDEX idx_outbox_event_unpublished;

CREATE INDEX idx_outbox_event_unpublished
    ON outbox_event (sequence_number)
    WHERE published_at IS NULL;
