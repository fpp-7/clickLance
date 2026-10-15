CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE CHECK (email = lower(btrim(email)) AND length(email) > 0),
    password_hash VARCHAR(100) NOT NULL CHECK (password_hash ~ '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$'),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    platform_admin BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE admin_session (
    jti_hash CHAR(64) PRIMARY KEY CHECK (jti_hash ~ '^[a-f0-9]{64}$'),
    user_id UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL CHECK (expires_at > created_at),
    revoked_at TIMESTAMPTZ
);
CREATE INDEX admin_session_user_idx ON admin_session(user_id, expires_at);

-- BE-004 acrescenta a FK assim que a entidade empresa passa a existir em V3.
CREATE TABLE admin_company_membership (
    user_id UUID NOT NULL REFERENCES app_user(id),
    empresa_id VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'COMPANY_ADMIN' CHECK (role = 'COMPANY_ADMIN'),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (user_id, empresa_id)
);
