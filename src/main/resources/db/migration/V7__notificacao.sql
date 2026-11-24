-- Notificações dentro do app. Push e e-mail são despachados pelo Notificador (regra 3.6).
CREATE TABLE notificacao
(
    id            UUID PRIMARY KEY,
    usuario_id    UUID          NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    tipo          VARCHAR(40)   NOT NULL,
    titulo        VARCHAR(120)  NOT NULL,
    mensagem      VARCHAR(1000) NOT NULL,
    ocorrencia_id UUID REFERENCES ocorrencia (id),
    lida_em       TIMESTAMPTZ,
    criada_em     TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_notificacao_tipo CHECK (tipo IN ('STATUS_ALTERADO', 'AVISO_ARQUIVAMENTO', 'OCORRENCIA_MESCLADA',
                                                   'CONTEUDO_REMOVIDO', 'CONTA_SUSPENSA', 'RESUMO_GESTOR'))
);

CREATE INDEX idx_notificacao_usuario ON notificacao (usuario_id, criada_em DESC);
CREATE INDEX idx_notificacao_nao_lidas ON notificacao (usuario_id) WHERE lida_em IS NULL;

CREATE TABLE preferencia_notificacao
(
    usuario_id    UUID PRIMARY KEY REFERENCES usuario (id) ON DELETE CASCADE,
    push          BOOLEAN     NOT NULL DEFAULT TRUE,
    email         BOOLEAN     NOT NULL DEFAULT FALSE,
    atualizada_em TIMESTAMPTZ NOT NULL
);

CREATE TABLE ocorrencia_silenciada
(
    usuario_id    UUID        NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    ocorrencia_id UUID        NOT NULL REFERENCES ocorrencia (id),
    criada_em     TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (usuario_id, ocorrencia_id)
);
