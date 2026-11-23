-- Execute como superusuário (postgres), uma única vez:
--   psql -U postgres -f scripts/criar-banco.sql
-- Pré-requisito: PostGIS instalado no servidor (Stack Builder > Spatial Extensions > PostGIS).

CREATE ROLE zelo LOGIN PASSWORD 'senhaaleatoria';
CREATE DATABASE zelo OWNER zelo ENCODING 'UTF8';

\connect zelo

-- PostGIS não é "trusted": precisa ser criada por superusuário. As migrations só confirmam que ela existe.
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS unaccent;
