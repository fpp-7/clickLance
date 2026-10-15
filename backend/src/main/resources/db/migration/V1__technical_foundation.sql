-- Apenas marco tecnico; entidades e politicas pertencem a historias posteriores.
CREATE TABLE backend_installation (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    installed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO backend_installation (id) VALUES (1);
