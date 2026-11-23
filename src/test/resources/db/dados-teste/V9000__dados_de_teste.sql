-- Dados fixos dos testes de integração. Os limites são retângulos simples, suficientes para as
-- validações geográficas; os ids são os códigos reais do IBGE.
INSERT INTO estado (id, nome)
VALUES (35, 'São Paulo'),
       (33, 'Rio de Janeiro');

INSERT INTO cidade (id, nome, estado_id, limite, ativo)
VALUES (3550308, 'São Paulo', 35,
        ST_GeomFromText('MULTIPOLYGON(((-46.83 -24.01, -46.36 -24.01, -46.36 -23.36, -46.83 -23.36, -46.83 -24.01)))', 4326),
        TRUE),
       (3304557, 'Rio de Janeiro', 33,
        ST_GeomFromText('MULTIPOLYGON(((-43.80 -23.08, -43.10 -23.08, -43.10 -22.75, -43.80 -22.75, -43.80 -23.08)))', 4326),
        TRUE),
       -- Cidade desativada pelo administrador: ocorrências nela são recusadas.
       (3509502, 'Campinas', 35,
        ST_GeomFromText('MULTIPOLYGON(((-47.20 -23.00, -46.90 -23.00, -46.90 -22.75, -47.20 -22.75, -47.20 -23.00)))', 4326),
        FALSE);
