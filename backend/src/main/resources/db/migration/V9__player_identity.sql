ALTER TABLE app_user ADD COLUMN player_account BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_user ADD COLUMN closed_at TIMESTAMPTZ;
ALTER TABLE app_user ADD CONSTRAINT closed_player_inactive CHECK (closed_at IS NULL OR NOT active);

CREATE TABLE player_session (
    jti_hash CHAR(64) PRIMARY KEY CHECK (jti_hash ~ '^[a-f0-9]{64}$'),
    user_id UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL CHECK (expires_at = created_at + interval '1 hour'),
    revoked_at TIMESTAMPTZ
);
CREATE INDEX player_session_user_idx ON player_session(user_id, expires_at);

CREATE TABLE player_password_reset (
    token_hash CHAR(64) PRIMARY KEY CHECK (token_hash ~ '^[a-f0-9]{64}$'),
    user_id UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL CHECK (expires_at = created_at + interval '15 minutes'),
    consumed_at TIMESTAMPTZ
);
CREATE INDEX player_password_reset_user_idx ON player_password_reset(user_id);
