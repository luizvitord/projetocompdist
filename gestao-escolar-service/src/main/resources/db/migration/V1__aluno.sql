-- Alunos (migrados do gestao-administrativa-service)

-- Parte sequencial do número de matrícula (ano + 6 dígitos)
CREATE SEQUENCE aluno_matricula_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE aluno (
    id               BIGSERIAL    PRIMARY KEY,
    matricula        VARCHAR(20)  NOT NULL,
    nome             VARCHAR(150) NOT NULL,
    data_nascimento  DATE         NOT NULL,
    endereco         VARCHAR(255),
    telefone         VARCHAR(30),
    email            VARCHAR(150),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_aluno_matricula UNIQUE (matricula)
);

CREATE INDEX idx_aluno_nome ON aluno (lower(nome));
