-- Migration: 0009_pdf_jobs.sql
-- Módulo PDF: jobs de merge

-- DOWN:
-- DROP TABLE IF EXISTS pdf_jobs;

CREATE TABLE pdf_jobs (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    status          TEXT        NOT NULL DEFAULT 'pending',
    file_a_name     TEXT        NOT NULL,
    file_b_name     TEXT        NOT NULL,
    file_a_path     TEXT        NOT NULL,
    file_b_path     TEXT        NOT NULL,
    result_path     TEXT,
    result_name     TEXT,
    error_message   TEXT,
    metadata        JSONB       NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_pdf_jobs_status
        CHECK (status IN ('pending', 'processing', 'completed', 'failed'))
);

CREATE TRIGGER trg_pdf_jobs_updated_at
    BEFORE UPDATE ON pdf_jobs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX idx_pdf_jobs_tenant_id ON pdf_jobs(tenant_id);
CREATE INDEX idx_pdf_jobs_user_id ON pdf_jobs(user_id);
CREATE INDEX idx_pdf_jobs_status ON pdf_jobs(tenant_id, status);
CREATE INDEX idx_pdf_jobs_created_at ON pdf_jobs(tenant_id, created_at DESC);

ALTER TABLE pdf_jobs ENABLE ROW LEVEL SECURITY;

CREATE POLICY pdf_jobs_tenant_select ON pdf_jobs
FOR SELECT USING (
    tenant_id IN (
        SELECT tenant_id FROM user_tenants
        WHERE user_id = auth.uid() AND is_active = TRUE
    )
);

CREATE POLICY pdf_jobs_tenant_insert ON pdf_jobs
FOR INSERT WITH CHECK (
    tenant_id IN (
        SELECT tenant_id FROM user_tenants
        WHERE user_id = auth.uid() AND is_active = TRUE
    )
    AND user_id = auth.uid()
);
