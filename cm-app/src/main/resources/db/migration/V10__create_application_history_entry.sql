CREATE TABLE application_history_entry
(
    id                  UUID           PRIMARY KEY,
    job_application_id  UUID           NOT NULL,
    application_version BIGINT         NOT NULL,
    previous_status     VARCHAR(20),
    status              VARCHAR(20)    NOT NULL,
    changed_by          VARCHAR(20)    NOT NULL,
    changed_by_user_id  UUID           NOT NULL,
    occurred_at         TIMESTAMPTZ(6) NOT NULL,
    CONSTRAINT application_history_entry_job_application_fkey FOREIGN KEY (job_application_id) REFERENCES job_application (id),
    CONSTRAINT application_history_entry_one_per_version UNIQUE (job_application_id, application_version),
    CONSTRAINT application_history_entry_changed_by_check CHECK (changed_by IN ('CANDIDATE', 'RECRUITER'))
);
