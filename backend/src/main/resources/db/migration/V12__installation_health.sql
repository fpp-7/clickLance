CREATE TABLE installation_health (
    dispositivo_id VARCHAR(32) PRIMARY KEY REFERENCES dispositivo(id),
    observed_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    gateway_version VARCHAR(32) NOT NULL,
    capture_state VARCHAR(16) NOT NULL CHECK(capture_state IN ('READY','WARMING_UP','UNAVAILABLE')),
    pending_events INTEGER NOT NULL CHECK(pending_events BETWEEN 0 AND 1000000),
    oldest_pending_age_seconds BIGINT NOT NULL CHECK(oldest_pending_age_seconds BETWEEN 0 AND 315360000),
    capture_progress_age_seconds BIGINT CHECK(capture_progress_age_seconds BETWEEN 0 AND 315360000)
);
