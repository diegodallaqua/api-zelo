-- Tipos de conteúdo moderável. Os ids são fixos: o código da aplicação (enum TipoConteudo) depende deles.
CREATE TABLE tipo_conteudo
(
    id   INTEGER PRIMARY KEY,
    nome VARCHAR(30) NOT NULL UNIQUE
);

INSERT INTO tipo_conteudo (id, nome)
VALUES (1, 'Ocorrência'),
       (2, 'Comentário');

-- Toda decisão de moderação fica registrada com o motivo (fluxo 2.3).
-- Decisões sobre conteúdo apontam para exatamente uma ocorrência OU um comentário;
-- suspensão e reativação diretas de usuário não têm conteúdo.
CREATE TABLE decisao_moderacao
(
    id                      UUID PRIMARY KEY,
    moderador_id            UUID        NOT NULL REFERENCES usuario (id),
    acao                    VARCHAR(30) NOT NULL,
    tipo_conteudo_id        INTEGER REFERENCES tipo_conteudo (id),
    ocorrencia_id           UUID REFERENCES ocorrencia (id),
    comentario_id           UUID REFERENCES comentario (id),
    usuario_afetado_id      UUID REFERENCES usuario (id),
    ocorrencia_principal_id UUID REFERENCES ocorrencia (id),
    suspensao_ate           TIMESTAMPTZ,
    suspensao_permanente    BOOLEAN     NOT NULL DEFAULT FALSE,
    motivo                  TEXT        NOT NULL,
    decidida_em             TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_decisao_acao CHECK (acao IN ('MANTER', 'REMOVER_CONTEUDO', 'MESCLAR_DUPLICATA', 'SUSPENDER_AUTOR',
                                               'SUSPENDER_USUARIO', 'REATIVAR_USUARIO')),
    CONSTRAINT ck_decisao_alvo CHECK (
        (acao IN ('SUSPENDER_USUARIO', 'REATIVAR_USUARIO')
            AND tipo_conteudo_id IS NULL AND ocorrencia_id IS NULL AND comentario_id IS NULL)
            OR (acao NOT IN ('SUSPENDER_USUARIO', 'REATIVAR_USUARIO') AND (
            (tipo_conteudo_id = 1 AND ocorrencia_id IS NOT NULL AND comentario_id IS NULL)
                OR (tipo_conteudo_id = 2 AND comentario_id IS NOT NULL AND ocorrencia_id IS NULL)))
        ),
    CONSTRAINT ck_decisao_mesclagem CHECK (acao <> 'MESCLAR_DUPLICATA' OR ocorrencia_principal_id IS NOT NULL)
);

CREATE INDEX idx_decisao_ocorrencia ON decisao_moderacao (ocorrencia_id);
CREATE INDEX idx_decisao_comentario ON decisao_moderacao (comentario_id);
CREATE INDEX idx_decisao_usuario_afetado ON decisao_moderacao (usuario_afetado_id);

CREATE TRIGGER trg_decisao_moderacao_imutavel
    BEFORE UPDATE OR DELETE
    ON decisao_moderacao
    FOR EACH ROW
EXECUTE FUNCTION impedir_alteracao_registro();

-- Denúncia de exatamente uma ocorrência OU um comentário.
CREATE TABLE denuncia
(
    id               UUID PRIMARY KEY,
    denunciante_id   UUID        NOT NULL REFERENCES usuario (id),
    tipo_conteudo_id INTEGER     NOT NULL REFERENCES tipo_conteudo (id),
    ocorrencia_id    UUID REFERENCES ocorrencia (id),
    comentario_id    UUID REFERENCES comentario (id),
    -- Cidade do conteúdo, para montar a fila de cada moderador.
    cidade_id        INTEGER     NOT NULL REFERENCES cidade (id),
    motivo           VARCHAR(30) NOT NULL,
    descricao        VARCHAR(500),
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    decisao_id       UUID REFERENCES decisao_moderacao (id),
    criada_em        TIMESTAMPTZ NOT NULL,
    atualizada_em    TIMESTAMPTZ NOT NULL,
    resolvida_em     TIMESTAMPTZ,
    CONSTRAINT ck_denuncia_alvo CHECK (
        (tipo_conteudo_id = 1 AND ocorrencia_id IS NOT NULL AND comentario_id IS NULL)
            OR (tipo_conteudo_id = 2 AND comentario_id IS NOT NULL AND ocorrencia_id IS NULL)),
    CONSTRAINT ck_denuncia_motivo CHECK (motivo IN ('SPAM', 'OFENSA', 'INFORMACAO_FALSA', 'LOCAL_ERRADO', 'OUTRO')),
    CONSTRAINT ck_denuncia_status CHECK (status IN ('PENDENTE', 'PROCEDENTE', 'IMPROCEDENTE')),
    CONSTRAINT ck_denuncia_outro_com_descricao CHECK (motivo <> 'OUTRO' OR descricao IS NOT NULL)
);

-- Um usuário denuncia o mesmo conteúdo uma única vez.
CREATE UNIQUE INDEX uk_denuncia_ocorrencia_por_usuario ON denuncia (denunciante_id, ocorrencia_id)
    WHERE ocorrencia_id IS NOT NULL;
CREATE UNIQUE INDEX uk_denuncia_comentario_por_usuario ON denuncia (denunciante_id, comentario_id)
    WHERE comentario_id IS NOT NULL;
CREATE INDEX idx_denuncia_fila ON denuncia (cidade_id, status, criada_em);
CREATE INDEX idx_denuncia_ocorrencia_pendente ON denuncia (ocorrencia_id) WHERE status = 'PENDENTE';
CREATE INDEX idx_denuncia_comentario_pendente ON denuncia (comentario_id) WHERE status = 'PENDENTE';
