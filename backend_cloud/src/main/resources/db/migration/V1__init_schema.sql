CREATE TABLE empresas (
    id UUID PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    dias_retencao_padrao INT NOT NULL DEFAULT 2 CHECK (dias_retencao_padrao BETWEEN 1 AND 30),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE quadras (
    id UUID PRIMARY KEY,
    empresa_id UUID NOT NULL REFERENCES empresas(id),
    nome VARCHAR(100) NOT NULL,
    fuso_horario VARCHAR(80) NOT NULL DEFAULT 'America/Sao_Paulo',
    duracao_segmento_segundos INT NOT NULL DEFAULT 60 CHECK (duracao_segmento_segundos BETWEEN 1 AND 60),
    segmentos_concatenar INT NOT NULL DEFAULT 2 CHECK (segmentos_concatenar BETWEEN 1 AND 300),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK (duracao_segmento_segundos * segmentos_concatenar <= 600)
);
CREATE INDEX idx_quadras_empresa ON quadras(empresa_id);
CREATE TABLE dispositivos (
    id VARCHAR(50) PRIMARY KEY,
    quadra_id UUID NOT NULL REFERENCES quadras(id),
    token_acesso_hash VARCHAR(255) NOT NULL,
    descricao VARCHAR(150),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_dispositivo_quadra UNIQUE (id, quadra_id)
);
CREATE TABLE lances (
    id UUID PRIMARY KEY,
    quadra_id UUID NOT NULL REFERENCES quadras(id),
    dispositivo_id VARCHAR(50) NOT NULL,
    evento_id UUID NOT NULL,
    timestamp_evento TIMESTAMP WITH TIME ZONE NOT NULL,
    s3_key VARCHAR(255) NOT NULL UNIQUE,
    tamanho_bytes BIGINT NOT NULL CHECK (tamanho_bytes > 0),
    status VARCHAR(25) NOT NULL DEFAULT 'DISPONIVEL'
        CHECK (status IN ('DISPONIVEL','EXPIRADO_SALVO','PURGA_PENDENTE','EXPIRADO_PURGADO')),
    expira_em TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    purga_tentativas INT NOT NULL DEFAULT 0,
    purga_proxima_tentativa TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_lance_origem FOREIGN KEY (dispositivo_id,quadra_id) REFERENCES dispositivos(id,quadra_id),
    CONSTRAINT uk_lance_evento UNIQUE (dispositivo_id,evento_id)
);
CREATE INDEX idx_lances_grade ON lances(quadra_id,timestamp_evento,status);
CREATE INDEX idx_lances_expiracao ON lances(status,expira_em);
CREATE INDEX idx_lances_purga_retry ON lances(status,purga_proxima_tentativa);
CREATE TABLE usuarios (
    id UUID PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE CHECK (email = LOWER(email)),
    senha_hash VARCHAR(255) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE usuario_lances (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    lance_id UUID NOT NULL REFERENCES lances(id),
    salvo_em TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_usuario_lance UNIQUE (usuario_id,lance_id)
);
CREATE INDEX idx_usuario_lances_lance ON usuario_lances(lance_id);
CREATE INDEX idx_usuario_lances_biblioteca ON usuario_lances(usuario_id,salvo_em DESC);
