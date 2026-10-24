CREATE TABLE processed_event
(
    event_id     UUID         NOT NULL PRIMARY KEY,
    processed_at TIMESTAMP(6) NOT NULL
);
