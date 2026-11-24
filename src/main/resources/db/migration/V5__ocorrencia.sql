CREATE TABLE ocorrencia
(
    id                      UUID PRIMARY KEY,
    -- Nulo após anonimização (exclusão de conta ou política de retenção).
    autor_id                UUID REFERENCES usuario (id),
    categoria_id            UUID             NOT NULL REFERENCES categoria (id),
    cidade_id               INTEGER          NOT NULL REFERENCES cidade (id),
    descricao               VARCHAR(1000)    NOT NULL,
    latitude                DOUBLE PRECISION NOT NULL,
    longitude               DOUBLE PRECISION NOT NULL,
    -- Derivado de latitude/longitude; usado nas consultas espaciais.
    ponto                   geometry(Point, 4326) GENERATED ALWAYS AS (
        ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)) STORED,
    status                  VARCHAR(20)      NOT NULL,
    anonima                 BOOLEAN          NOT NULL DEFAULT FALSE,
    total_curtidas          INTEGER          NOT NULL DEFAULT 0,
    prioridade              INTEGER          NOT NULL DEFAULT 0,
    -- Preenchido quando a ocorrência é mesclada (status DUPLICADA).
    ocorrencia_principal_id UUID REFERENCES ocorrencia (id),
    resolvida_em            TIMESTAMPTZ,
    resolucao_confirmada_em TIMESTAMPTZ,
    ultima_atividade_em     TIMESTAMPTZ      NOT NULL,
    aviso_arquivamento_em   TIMESTAMPTZ,
    -- Exclusão lógica (pelo autor ou pela moderação).
    removida_em             TIMESTAMPTZ,
    motivo_remocao          TEXT,
    criada_em               TIMESTAMPTZ      NOT NULL,
    atualizada_em           TIMESTAMPTZ      NOT NULL,
    versao                  BIGINT           NOT NULL DEFAULT 0,
    CONSTRAINT ck_ocorrencia_status CHECK (status IN
                                           ('EM_ANALISE', 'ABERTA', 'EM_ANDAMENTO', 'RESOLVIDA', 'REJEITADA',
                                            'DUPLICADA', 'ARQUIVADA')),
    CONSTRAINT ck_ocorrencia_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_ocorrencia_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_ocorrencia_curtidas CHECK (total_curtidas >= 0),
    CONSTRAINT ck_ocorrencia_duplicada CHECK ((status = 'DUPLICADA') = (ocorrencia_principal_id IS NOT NULL)),
    CONSTRAINT ck_ocorrencia_principal_diferente CHECK (ocorrencia_principal_id <> id)
);

-- Mapa por área visível (bounding box).
CREATE INDEX idx_ocorrencia_ponto ON ocorrencia USING GIST (ponto) WHERE removida_em IS NULL;
-- Busca de duplicidade por raio em metros.
CREATE INDEX idx_ocorrencia_ponto_geografia ON ocorrencia USING GIST ((ponto::geography)) WHERE removida_em IS NULL;
CREATE INDEX idx_ocorrencia_cidade_status ON ocorrencia (cidade_id, status) WHERE removida_em IS NULL;
CREATE INDEX idx_ocorrencia_categoria ON ocorrencia (categoria_id) WHERE removida_em IS NULL;
CREATE INDEX idx_ocorrencia_autor_criada ON ocorrencia (autor_id, criada_em);
CREATE INDEX idx_ocorrencia_status_atividade ON ocorrencia (status, ultima_atividade_em) WHERE removida_em IS NULL;
CREATE INDEX idx_ocorrencia_criada ON ocorrencia (criada_em);

CREATE TABLE foto_ocorrencia
(
    id            UUID PRIMARY KEY,
    ocorrencia_id UUID         NOT NULL REFERENCES ocorrencia (id),
    -- Chave do objeto no armazenamento (disco local ou Cloudflare R2).
    chave         VARCHAR(255) NOT NULL UNIQUE,
    content_type  VARCHAR(50)  NOT NULL,
    tamanho_bytes INTEGER      NOT NULL,
    largura       INTEGER      NOT NULL,
    altura        INTEGER      NOT NULL,
    ordem         INTEGER      NOT NULL,
    criada_em     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_foto_ocorrencia ON foto_ocorrencia (ocorrencia_id, ordem);

-- Histórico imutável do ciclo de vida (regra 3.4 / RF15).
-- autor_id só é gravado para ações da equipe; ações do autor da ocorrência usam papel_autor = 'AUTOR',
-- assim a anonimização da ocorrência não deixa vínculo com o cidadão no histórico.
CREATE TABLE historico_status
(
    id              UUID PRIMARY KEY,
    ocorrencia_id   UUID        NOT NULL REFERENCES ocorrencia (id),
    tipo_evento     VARCHAR(20) NOT NULL,
    status_anterior VARCHAR(20),
    status_novo     VARCHAR(20) NOT NULL,
    autor_id        UUID REFERENCES usuario (id),
    papel_autor     VARCHAR(20) NOT NULL,
    motivo          TEXT,
    ocorrido_em     TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_historico_tipo_evento CHECK (tipo_evento IN ('CRIACAO', 'TRANSICAO', 'CONTESTACAO', 'CONFIRMACAO')),
    CONSTRAINT ck_historico_papel CHECK (papel_autor IN ('AUTOR', 'MODERADOR', 'GESTOR', 'ADMIN', 'SISTEMA'))
);

CREATE INDEX idx_historico_ocorrencia ON historico_status (ocorrencia_id, ocorrido_em);

CREATE TRIGGER trg_historico_status_imutavel
    BEFORE UPDATE OR DELETE
    ON historico_status
    FOR EACH ROW
EXECUTE FUNCTION impedir_alteracao_registro();

-- "Também vejo esse problema": uma curtida por usuário por ocorrência (RF11).
CREATE TABLE curtida
(
    ocorrencia_id UUID        NOT NULL REFERENCES ocorrencia (id),
    usuario_id    UUID        NOT NULL REFERENCES usuario (id),
    criada_em     TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (ocorrencia_id, usuario_id)
);

CREATE INDEX idx_curtida_usuario ON curtida (usuario_id);

CREATE TABLE comentario
(
    id             UUID PRIMARY KEY,
    ocorrencia_id  UUID         NOT NULL REFERENCES ocorrencia (id),
    autor_id       UUID REFERENCES usuario (id),
    texto          VARCHAR(500) NOT NULL,
    criado_em      TIMESTAMPTZ  NOT NULL,
    editado_em     TIMESTAMPTZ,
    removido_em    TIMESTAMPTZ,
    motivo_remocao TEXT
);

CREATE INDEX idx_comentario_ocorrencia ON comentario (ocorrencia_id, criado_em) WHERE removido_em IS NULL;
CREATE INDEX idx_comentario_autor ON comentario (autor_id);
CREATE INDEX idx_comentario_criado ON comentario (criado_em);
