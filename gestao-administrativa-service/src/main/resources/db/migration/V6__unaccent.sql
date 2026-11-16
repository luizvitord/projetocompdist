-- Busca sem diferenciar acentos (parâmetro "busca" das listagens): "avaliacao" encontra "Avaliação".
-- unaccent vem no contrib da imagem postgres:17 e é uma extensão "trusted" (PostgreSQL 13+): o dono
-- do banco pode criá-la sem ser superusuário.
CREATE EXTENSION IF NOT EXISTS unaccent;
