CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE FUNCTION job_offer_locations_search_text(locations VARCHAR[]) RETURNS TEXT
    LANGUAGE sql
    IMMUTABLE
    PARALLEL SAFE
    RETURN lower(array_to_string(locations, '|'));

CREATE INDEX idx_job_offer_published_listing ON job_offer (published_at DESC, id DESC) WHERE status = 'PUBLISHED';

CREATE INDEX idx_job_offer_title_trgm ON job_offer USING gin (lower(title) gin_trgm_ops);

CREATE INDEX idx_job_offer_company_name_trgm ON job_offer USING gin (lower(company_name) gin_trgm_ops);

CREATE INDEX idx_job_offer_locations_trgm ON job_offer USING gin (job_offer_locations_search_text(locations) gin_trgm_ops);
