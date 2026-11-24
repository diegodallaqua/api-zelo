-- Categorias de ocorrência, válidas em todas as cidades e configuráveis pelo administrador (regra 3.2).
CREATE TABLE categoria
(
    id            UUID PRIMARY KEY,
    nome          VARCHAR(60) NOT NULL,
    descricao     VARCHAR(255),
    icone         VARCHAR(60),
    ativa         BOOLEAN     NOT NULL DEFAULT TRUE,
    criada_em     TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizada_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_categoria_nome ON categoria (lower(nome));
