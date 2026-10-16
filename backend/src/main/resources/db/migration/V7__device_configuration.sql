CREATE TABLE device_configuration (
    version UUID PRIMARY KEY,
    dispositivo_id VARCHAR(32) NOT NULL REFERENCES dispositivo(id),
    settings JSONB NOT NULL CHECK (jsonb_typeof(settings) = 'object'),
    published_by UUID NOT NULL,
    published_at TIMESTAMPTZ NOT NULL,
    UNIQUE(dispositivo_id, version)
);
CREATE TABLE device_configuration_current (
    dispositivo_id VARCHAR(32) PRIMARY KEY REFERENCES dispositivo(id),
    version UUID NOT NULL,
    FOREIGN KEY(dispositivo_id, version) REFERENCES device_configuration(dispositivo_id, version)
);
CREATE INDEX device_configuration_history ON device_configuration(dispositivo_id, published_at, version);
