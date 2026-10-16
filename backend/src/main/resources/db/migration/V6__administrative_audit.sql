CREATE TABLE administrative_audit (
    id UUID PRIMARY KEY,
    correlation_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    actor_id UUID,
    actor_type VARCHAR(16) NOT NULL CHECK (actor_type IN ('ADMIN', 'ANONYMOUS', 'SYSTEM')),
    action VARCHAR(64) NOT NULL CHECK (action ~ '^[A-Z_]{1,64}$'),
    resource_type VARCHAR(32) NOT NULL CHECK (resource_type ~ '^[A-Z_]{1,32}$'),
    resource_id VARCHAR(64) CHECK (resource_id ~ '^[A-Za-z0-9_-]{1,64}$'),
    empresa_id VARCHAR(64) CHECK (empresa_id ~ '^[A-Za-z0-9_-]{1,64}$'),
    outcome VARCHAR(16) NOT NULL CHECK (outcome IN ('SUCCESS', 'REFUSED', 'FAILED')),
    code VARCHAR(64) NOT NULL CHECK (code ~ '^[A-Z_]{1,64}$'),
    http_status INTEGER CHECK (http_status BETWEEN 100 AND 599),
    CHECK ((actor_type = 'ADMIN' AND actor_id IS NOT NULL) OR (actor_type <> 'ADMIN' AND actor_id IS NULL))
);
-- Snapshots de identidade, sem FK: o historico nao depende da existencia futura do recurso.
CREATE INDEX administrative_audit_company_idx ON administrative_audit(empresa_id, occurred_at DESC, id DESC);
CREATE INDEX administrative_audit_correlation_idx ON administrative_audit(correlation_id, occurred_at, id);
