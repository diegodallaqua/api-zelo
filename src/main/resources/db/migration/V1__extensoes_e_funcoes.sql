-- PostGIS não é uma extensão "trusted": se o usuário da aplicação não for superusuário,
-- crie-a antes como superusuário (veja scripts/criar-banco.sql). Aqui vira no-op.
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS unaccent;

-- unaccent() não é IMMUTABLE; este wrapper permite usá-la em expressões e índices.
CREATE OR REPLACE FUNCTION f_unaccent(texto TEXT)
    RETURNS TEXT
    LANGUAGE sql
    IMMUTABLE PARALLEL SAFE STRICT
AS
$$
SELECT public.unaccent('public.unaccent', texto)
$$;

-- Usada por triggers que tornam tabelas append-only (histórico de status, auditoria, decisões).
CREATE OR REPLACE FUNCTION impedir_alteracao_registro()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    RAISE EXCEPTION 'Registros da tabela % são imutáveis', TG_TABLE_NAME
        USING ERRCODE = 'restrict_violation';
END;
$$;
