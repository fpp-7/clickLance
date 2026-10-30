CREATE TABLE purge_job (
    event_id VARCHAR(32) PRIMARY KEY REFERENCES replay(evento_id) ON DELETE CASCADE,
    state VARCHAR(16) NOT NULL CHECK(state IN ('PENDING','RUNNING','DONE')),
    reserved_at TIMESTAMPTZ NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK(attempts >= 0),
    lease_token UUID,
    lease_until TIMESTAMPTZ,
    last_error VARCHAR(64) CHECK(last_error ~ '^[A-Z_]{1,64}$'),
    completed_at TIMESTAMPTZ,
    CHECK((state='RUNNING') = (lease_token IS NOT NULL AND lease_until IS NOT NULL)),
    CHECK((state='DONE') = (completed_at IS NOT NULL))
);
CREATE INDEX purge_job_work ON purge_job(next_attempt_at,event_id) WHERE state <> 'DONE';
CREATE TABLE reconciliation_issue (
    id UUID PRIMARY KEY,
    empresa_id VARCHAR(64) NOT NULL REFERENCES empresa(id) ON DELETE CASCADE,
    bucket VARCHAR(63) NOT NULL,
    object_key VARCHAR(1024) NOT NULL,
    kind VARCHAR(32) NOT NULL CHECK(kind IN ('ORPHAN_OBJECT','MISSING_OBJECT','PURGED_OBJECT_PRESENT')),
    first_seen_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    UNIQUE(empresa_id,bucket,object_key,kind)
);
