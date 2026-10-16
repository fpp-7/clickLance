-- Origem composta garante coerencia mesmo em escritas fora da API.
ALTER TABLE quadra ADD CONSTRAINT quadra_origin_unique UNIQUE(id, empresa_id);
ALTER TABLE dispositivo ADD CONSTRAINT dispositivo_origin_unique UNIQUE(id, quadra_id);
CREATE TABLE replay (
    evento_id VARCHAR(32) PRIMARY KEY CHECK(evento_id ~ '^[a-f0-9]{32}$'),
    dispositivo_id VARCHAR(32) NOT NULL,
    quadra_id VARCHAR(64) NOT NULL,
    empresa_id VARCHAR(64) NOT NULL,
    event_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    retention_days INTEGER NOT NULL CHECK(retention_days BETWEEN 1 AND 30),
    expires_at TIMESTAMPTZ NOT NULL,
    expired_on_arrival BOOLEAN NOT NULL,
    bucket VARCHAR(63) NOT NULL CHECK(bucket ~ '^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$'),
    object_key VARCHAR(80) NOT NULL,
    sha256 CHAR(64) NOT NULL CHECK(sha256 ~ '^[a-f0-9]{64}$'),
    object_version TEXT,
    object_etag TEXT NOT NULL,
    object_size BIGINT NOT NULL CHECK(object_size > 0),
    purged_at TIMESTAMPTZ,
    FOREIGN KEY(dispositivo_id, quadra_id) REFERENCES dispositivo(id, quadra_id),
    FOREIGN KEY(quadra_id, empresa_id) REFERENCES quadra(id, empresa_id),
    UNIQUE(bucket, object_key),
    CHECK(object_key = 'replays/' || dispositivo_id || '/' || evento_id || '.mp4'),
    CHECK(expires_at = event_at + retention_days * INTERVAL '24 hours'),
    CHECK(expired_on_arrival = (expires_at <= received_at))
);
CREATE INDEX replay_court_time ON replay(quadra_id, event_at, evento_id);
CREATE INDEX replay_expiry ON replay(expires_at, evento_id) WHERE purged_at IS NULL;
CREATE FUNCTION prevent_replay_identity_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (to_jsonb(NEW) - 'purged_at') IS DISTINCT FROM (to_jsonb(OLD) - 'purged_at') THEN
        RAISE EXCEPTION 'Replay identity and retention are immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER replay_identity_immutable BEFORE UPDATE ON replay
FOR EACH ROW EXECUTE FUNCTION prevent_replay_identity_change();
