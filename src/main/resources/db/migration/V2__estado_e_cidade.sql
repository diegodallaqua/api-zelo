-- Estados e cidades brasileiros, importados das APIs de localidades e malhas do IBGE.
-- Os ids são os códigos do IBGE (UF com 2 dígitos, cidade com 7).
CREATE TABLE estado
(
    id   INTEGER PRIMARY KEY,
    nome VARCHAR(60) NOT NULL UNIQUE
);

CREATE TABLE cidade
(
    id                      INTEGER PRIMARY KEY,
    nome                    VARCHAR(120)                 NOT NULL,
    estado_id               INTEGER                      NOT NULL REFERENCES estado (id),
    -- Polígono da cidade: valida se o ponto da ocorrência e o dispositivo estão nela (RF09).
    limite                  geometry(MultiPolygon, 4326) NOT NULL,
    ativo                   BOOLEAN                      NOT NULL DEFAULT TRUE,
    -- Textos legais e de orientação podem variar por cidade (regra 3.9).
    aviso_canal_nao_oficial TEXT,
    url_canal_oficial       VARCHAR(500),
    texto_legal             TEXT,
    criado_em               TIMESTAMPTZ                  NOT NULL DEFAULT now(),
    atualizado_em           TIMESTAMPTZ                  NOT NULL DEFAULT now()
);

CREATE INDEX idx_cidade_limite ON cidade USING GIST (limite);
CREATE INDEX idx_cidade_estado ON cidade (estado_id);
