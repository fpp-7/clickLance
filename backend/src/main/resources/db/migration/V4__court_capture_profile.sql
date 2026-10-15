CREATE TABLE quadra (
    id VARCHAR(64) PRIMARY KEY CHECK (id ~ '^[A-Za-z0-9_-]{1,64}$'),
    empresa_id VARCHAR(64) NOT NULL REFERENCES empresa(id),
    nome VARCHAR(120) NOT NULL CHECK (length(btrim(nome)) > 0),
    active BOOLEAN NOT NULL,
    timezone VARCHAR(64) NOT NULL CHECK (length(btrim(timezone)) > 0),
    replay_before_seconds DOUBLE PRECISION NOT NULL CHECK (replay_before_seconds BETWEEN 1 AND 600),
    replay_after_seconds DOUBLE PRECISION NOT NULL CHECK (replay_after_seconds BETWEEN 0 AND 60),
    segment_duration_seconds INTEGER NOT NULL CHECK (segment_duration_seconds BETWEEN 1 AND 120),
    keyframe_interval INTEGER NOT NULL CHECK (keyframe_interval BETWEEN 1 AND 600),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX quadra_empresa_idx ON quadra(empresa_id, id);

-- D10: nao transferir a origem historica de uma quadra entre empresas.
CREATE FUNCTION prevent_quadra_company_transfer() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.empresa_id IS DISTINCT FROM OLD.empresa_id THEN
        RAISE EXCEPTION 'Quadra company is immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER quadra_company_immutable BEFORE UPDATE OF empresa_id ON quadra
FOR EACH ROW EXECUTE FUNCTION prevent_quadra_company_transfer();
