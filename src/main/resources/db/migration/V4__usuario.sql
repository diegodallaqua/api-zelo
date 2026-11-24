-- Perfis de acesso. Os ids são fixos: o código da aplicação (enum Perfil) depende deles.
CREATE TABLE perfil_usuario
(
    id   INTEGER PRIMARY KEY,
    nome VARCHAR(30) NOT NULL UNIQUE
);

INSERT INTO perfil_usuario (id, nome)
VALUES (1, 'Cidadão'),
       (2, 'Moderador'),
       (3, 'Gestor público'),
       (4, 'Administrador');

-- Endereço do usuário. Para moderadores e gestores, a cidade do endereço define a área de atuação.
CREATE TABLE endereco
(
    id            UUID PRIMARY KEY,
    cep           VARCHAR(8)   NOT NULL,
    rua           VARCHAR(150) NOT NULL,
    bairro        VARCHAR(100) NOT NULL,
    numero        VARCHAR(10)  NOT NULL,
    complemento   VARCHAR(100),
    cidade_id     INTEGER      NOT NULL REFERENCES cidade (id),
    criado_em     TIMESTAMPTZ  NOT NULL,
    atualizado_em TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_endereco_cep CHECK (cep ~ '^[0-9]{8}$')
);

CREATE INDEX idx_endereco_cidade ON endereco (cidade_id);

CREATE TABLE usuario
(
    id                   UUID PRIMARY KEY,
    nome                 VARCHAR(100) NOT NULL,
    email                VARCHAR(254) NOT NULL,
    senha_hash           VARCHAR(100),
    perfil_id            INTEGER      NOT NULL REFERENCES perfil_usuario (id),
    -- Obrigatório para contas ativas; fica nulo só depois da exclusão da conta (LGPD).
    endereco_id          UUID REFERENCES endereco (id),
    suspenso_ate         TIMESTAMPTZ,
    suspensao_permanente BOOLEAN      NOT NULL DEFAULT FALSE,
    motivo_suspensao     TEXT,
    -- Conta excluída a pedido do titular (LGPD): dados pessoais anonimizados.
    excluido_em          TIMESTAMPTZ,
    criado_em            TIMESTAMPTZ  NOT NULL,
    atualizado_em        TIMESTAMPTZ  NOT NULL,
    versao               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_usuario_endereco_obrigatorio CHECK (excluido_em IS NOT NULL OR endereco_id IS NOT NULL)
);

CREATE UNIQUE INDEX uk_usuario_email ON usuario (lower(email));
CREATE INDEX idx_usuario_perfil ON usuario (perfil_id);
CREATE INDEX idx_usuario_endereco ON usuario (endereco_id);

CREATE TABLE refresh_token
(
    id          UUID PRIMARY KEY,
    usuario_id  UUID        NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    -- Guardamos apenas o SHA-256 do token.
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expira_em   TIMESTAMPTZ NOT NULL,
    revogado_em TIMESTAMPTZ,
    criado_em   TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_refresh_token_usuario ON refresh_token (usuario_id);

CREATE TABLE consentimento
(
    id            UUID PRIMARY KEY,
    usuario_id    UUID        NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    tipo          VARCHAR(30) NOT NULL,
    versao        VARCHAR(20) NOT NULL,
    aceito        BOOLEAN     NOT NULL,
    registrado_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_consentimento_tipo CHECK (tipo IN ('TERMOS_DE_USO', 'POLITICA_DE_PRIVACIDADE', 'LOCALIZACAO'))
);

CREATE INDEX idx_consentimento_usuario ON consentimento (usuario_id, tipo, registrado_em DESC);
