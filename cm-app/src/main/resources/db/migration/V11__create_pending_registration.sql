CREATE TABLE pending_registration
(
    id         UUID           PRIMARY KEY,
    created_at TIMESTAMPTZ(6) NOT NULL
);

CREATE INDEX idx_pending_registration_created_at ON pending_registration (created_at);
