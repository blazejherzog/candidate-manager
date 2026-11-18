CREATE TABLE candidate
(
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL UNIQUE,
    email      VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(255) NOT NULL,
    last_name  VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ(6) NOT NULL
);
