CREATE TABLE job_offer
(
    id                UUID           PRIMARY KEY,
    recruiter_id      UUID           NOT NULL,
    status            VARCHAR(20)    NOT NULL,
    title             VARCHAR(120)   NOT NULL,
    company_name      VARCHAR(200)   NOT NULL,
    category          VARCHAR(30)    NOT NULL,
    position_level    VARCHAR(20)    NOT NULL,
    work_mode         VARCHAR(10)    NOT NULL,
    employment_time   VARCHAR(20)    NOT NULL,
    locations         VARCHAR(100)[] NOT NULL,
    description       VARCHAR(5000)  NOT NULL,
    responsibilities  VARCHAR(500)[] NOT NULL,
    requirements      VARCHAR(500)[] NOT NULL,
    nice_to_have      VARCHAR(500)[] NOT NULL,
    benefits          VARCHAR(500)[] NOT NULL,
    recruitment_steps VARCHAR(500)[] NOT NULL,
    start_date        DATE,
    valid_until       DATE,
    created_at        TIMESTAMPTZ(6) NOT NULL,
    updated_at        TIMESTAMPTZ(6) NOT NULL,
    published_at      TIMESTAMPTZ(6),
    version           BIGINT         NOT NULL,
    CONSTRAINT job_offer_title_length_check CHECK (char_length(btrim(title)) >= 5),
    CONSTRAINT job_offer_status_check CHECK (
        (status = 'DRAFT' AND published_at IS NULL)
        OR (status = 'PUBLISHED' AND published_at IS NOT NULL AND valid_until IS NOT NULL)),
    CONSTRAINT job_offer_category_check CHECK (category IN (
        'IT', 'SALES', 'FINANCE', 'HR', 'MARKETING', 'CUSTOMER_SERVICE', 'LOGISTICS', 'PRODUCTION',
        'ENGINEERING', 'CONSTRUCTION', 'HEALTHCARE', 'EDUCATION', 'LAW', 'ADMINISTRATION', 'OTHER')),
    CONSTRAINT job_offer_position_level_check CHECK (position_level IN (
        'INTERN', 'JUNIOR', 'MID', 'SENIOR', 'MANAGER', 'DIRECTOR', 'PHYSICAL_WORKER')),
    CONSTRAINT job_offer_work_mode_check CHECK (work_mode IN ('REMOTE', 'HYBRID', 'OFFICE')),
    CONSTRAINT job_offer_employment_time_check CHECK (employment_time IN ('FULL_TIME', 'PART_TIME', 'ADDITIONAL')),
    CONSTRAINT job_offer_location_required_check CHECK (work_mode = 'REMOTE' OR cardinality(locations) > 0),
    CONSTRAINT job_offer_responsibilities_required_check CHECK (cardinality(responsibilities) > 0),
    CONSTRAINT job_offer_requirements_required_check CHECK (cardinality(requirements) > 0)
);

CREATE INDEX idx_job_offer_recruiter_created_at ON job_offer (recruiter_id, created_at DESC);

CREATE TABLE job_offer_salary
(
    job_offer_id  UUID           NOT NULL REFERENCES job_offer (id) ON DELETE CASCADE,
    contract_type VARCHAR(20)    NOT NULL,
    min_amount    NUMERIC(12, 2) NOT NULL,
    max_amount    NUMERIC(12, 2) NOT NULL,
    currency      VARCHAR(3)     NOT NULL,
    period        VARCHAR(10)    NOT NULL,
    PRIMARY KEY (job_offer_id, contract_type),
    CONSTRAINT job_offer_salary_contract_type_check CHECK (contract_type IN (
        'EMPLOYMENT', 'B2B', 'MANDATE', 'SPECIFIC_WORK', 'TEMPORARY_STAFFING', 'INTERNSHIP')),
    CONSTRAINT job_offer_salary_amount_check CHECK (min_amount > 0 AND max_amount >= min_amount),
    CONSTRAINT job_offer_salary_currency_check CHECK (currency IN ('PLN', 'EUR', 'USD')),
    CONSTRAINT job_offer_salary_period_check CHECK (period IN ('HOUR', 'MONTH', 'YEAR'))
);

CREATE TABLE job_offer_language
(
    job_offer_id UUID        NOT NULL REFERENCES job_offer (id) ON DELETE CASCADE,
    language     VARCHAR(50) NOT NULL,
    level        VARCHAR(2)  NOT NULL,
    PRIMARY KEY (job_offer_id, language),
    CONSTRAINT job_offer_language_level_check CHECK (level IN ('A1', 'A2', 'B1', 'B2', 'C1', 'C2'))
);
