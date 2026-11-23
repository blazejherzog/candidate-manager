ALTER TABLE candidate
    ADD COLUMN phone_number VARCHAR(16),
    ADD COLUMN city         VARCHAR(100),
    ADD COLUMN headline     VARCHAR(120),
    ADD COLUMN summary      VARCHAR(2000),
    ADD COLUMN updated_at   TIMESTAMPTZ(6),
    ADD COLUMN version      BIGINT;

UPDATE candidate
SET updated_at = created_at,
    version    = 0;

ALTER TABLE candidate
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN version SET NOT NULL,
    ADD CONSTRAINT candidate_phone_number_check CHECK (phone_number ~ '^\+[1-9][0-9]{7,14}$');
