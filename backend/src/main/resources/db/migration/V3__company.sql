CREATE TABLE empresa (
    id VARCHAR(64) PRIMARY KEY CHECK (id ~ '^[A-Za-z0-9_-]{1,64}$'),
    nome VARCHAR(120) NOT NULL CHECK (length(btrim(nome)) > 0),
    active BOOLEAN NOT NULL,
    retencao_dias INTEGER NOT NULL DEFAULT 2 CHECK (retencao_dias BETWEEN 1 AND 30),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE admin_company_membership
    ADD CONSTRAINT admin_membership_empresa_fk FOREIGN KEY (empresa_id) REFERENCES empresa(id);
CREATE INDEX admin_membership_empresa_idx ON admin_company_membership(empresa_id, user_id);
