CREATE TABLE dispositivo (
    id VARCHAR(32) PRIMARY KEY CHECK (id ~ '^[A-Za-z0-9_-]{1,32}$'),
    quadra_id VARCHAR(64) NOT NULL REFERENCES quadra(id),
    active BOOLEAN NOT NULL,
    bucket VARCHAR(63) NOT NULL CHECK (length(bucket) BETWEEN 3 AND 63
        AND bucket ~ '^[a-z0-9][a-z0-9.-]+[a-z0-9]$'),
    token_hash CHAR(64) NOT NULL UNIQUE CHECK (token_hash ~ '^[a-f0-9]{64}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX dispositivo_quadra_idx ON dispositivo(quadra_id, id);

-- Empresa derivada da quadra: impossivel persistir uma empresa contraditoria.
-- D10: trocar quadra exige um novo dispositivo, sem renomear a identidade CL1.
CREATE FUNCTION prevent_dispositivo_origin_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.id IS DISTINCT FROM OLD.id OR NEW.quadra_id IS DISTINCT FROM OLD.quadra_id THEN
        RAISE EXCEPTION 'Device identity and court are immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER dispositivo_origin_immutable BEFORE UPDATE OF id, quadra_id ON dispositivo
FOR EACH ROW EXECUTE FUNCTION prevent_dispositivo_origin_change();
