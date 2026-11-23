-- Trilha de auditoria das ações sensíveis (RNF15). Append-only.
CREATE TABLE registro_auditoria
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ator_id     UUID REFERENCES usuario (id),
    papel_ator  VARCHAR(20) NOT NULL,
    acao        VARCHAR(60) NOT NULL,
    entidade    VARCHAR(40) NOT NULL,
    entidade_id VARCHAR(64) NOT NULL,
    -- JSON com os detalhes da ação.
    detalhes    TEXT,
    ocorrido_em TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_auditoria_entidade ON registro_auditoria (entidade, entidade_id, ocorrido_em DESC);
CREATE INDEX idx_auditoria_ator ON registro_auditoria (ator_id, ocorrido_em DESC);

CREATE TRIGGER trg_registro_auditoria_imutavel
    BEFORE UPDATE OR DELETE
    ON registro_auditoria
    FOR EACH ROW
EXECUTE FUNCTION impedir_alteracao_registro();
