-- Usuários que acessam o DistriSchool e o perfil de cada um

CREATE TABLE usuario (
    id             BIGSERIAL    PRIMARY KEY,
    nome           VARCHAR(150) NOT NULL,
    email          VARCHAR(150) NOT NULL,
    senha_hash     VARCHAR(100) NOT NULL,
    perfil         VARCHAR(20)  NOT NULL,
    referencia_id  BIGINT,
    ativo          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_usuario_perfil CHECK (perfil IN ('ADMIN', 'PROFESSOR', 'ALUNO', 'RESPONSAVEL'))
);

-- Email único sem diferenciar maiúsculas/minúsculas
CREATE UNIQUE INDEX uk_usuario_email ON usuario (lower(email));

-- Administrador inicial, para o primeiro acesso (senha: 123456, hash BCrypt)
INSERT INTO usuario (nome, email, senha_hash, perfil)
VALUES ('Coordenação', 'coordenacao@distrischool.com',
        '$2a$10$WMnyC3S36dY6xp.zwpbyR.aIkXnx1uAczFjCE3FaIhVNEPrwqDCgS', 'ADMIN');
