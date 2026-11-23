CREATE TABLE job_application
(
    id           UUID           PRIMARY KEY,
    job_offer_id UUID           NOT NULL,
    candidate_id UUID           NOT NULL,
    recruiter_id UUID           NOT NULL,
    offer_title  VARCHAR(120)   NOT NULL,
    company_name VARCHAR(200)   NOT NULL,
    message      VARCHAR(2000),
    status       VARCHAR(20)    NOT NULL,
    submitted_at TIMESTAMPTZ(6) NOT NULL,
    updated_at   TIMESTAMPTZ(6) NOT NULL,
    version      BIGINT         NOT NULL,
    CONSTRAINT job_application_job_offer_fkey FOREIGN KEY (job_offer_id) REFERENCES job_offer (id),
    CONSTRAINT job_application_candidate_fkey FOREIGN KEY (candidate_id) REFERENCES candidate (id),
    CONSTRAINT job_application_one_per_candidate_and_offer UNIQUE (job_offer_id, candidate_id),
    CONSTRAINT job_application_status_check CHECK (status IN (
        'SUBMITTED', 'IN_REVIEW', 'INTERVIEW', 'HIRED', 'REJECTED', 'WITHDRAWN'))
);

CREATE INDEX idx_job_application_candidate_submitted_at ON job_application (candidate_id, submitted_at DESC, id DESC);

CREATE INDEX idx_job_application_job_offer_submitted_at ON job_application (job_offer_id, submitted_at DESC, id DESC);
