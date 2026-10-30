CREATE TABLE library_entry (
    user_id UUID NOT NULL REFERENCES app_user(id),
    event_id VARCHAR(32) NOT NULL REFERENCES replay(evento_id),
    saved_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(user_id, event_id)
);
CREATE INDEX library_owner_page ON library_entry(user_id, saved_at, event_id);
CREATE INDEX library_event_owners ON library_entry(event_id);

-- Lifecycle is separate from immutable replay identity/retention (V8).
CREATE TABLE replay_lifecycle (
    event_id VARCHAR(32) PRIMARY KEY REFERENCES replay(evento_id),
    blocked_at TIMESTAMPTZ,
    blocked_by UUID,
    block_reason VARCHAR(32),
    purge_reserved_at TIMESTAMPTZ,
    CHECK ((blocked_at IS NULL AND blocked_by IS NULL AND block_reason IS NULL)
        OR (blocked_at IS NOT NULL AND blocked_by IS NOT NULL AND block_reason ~ '^[A-Z_]{1,32}$'))
);
