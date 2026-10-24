-- Schema inicial do school-core-service: alunos, professores e turmas

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

CREATE TABLE professor (
    id            BIGSERIAL    PRIMARY KEY,
    nome          VARCHAR(150) NOT NULL,
    qualificacao  VARCHAR(255),
    email         VARCHAR(150) NOT NULL,
    telefone      VARCHAR(30),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_professor_email UNIQUE (email)
);

CREATE INDEX idx_professor_nome ON professor (lower(nome));

CREATE TABLE turma (
    id          BIGSERIAL    PRIMARY KEY,
    codigo      VARCHAR(30)  NOT NULL,
    nome        VARCHAR(100) NOT NULL,
    ano_letivo  INTEGER      NOT NULL,
    turno       VARCHAR(10)  NOT NULL,
    capacidade  INTEGER,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_turma_codigo UNIQUE (codigo),
    CONSTRAINT ck_turma_turno CHECK (turno IN ('MANHA', 'TARDE', 'NOITE', 'INTEGRAL')),
    CONSTRAINT ck_turma_capacidade CHECK (capacidade IS NULL OR capacidade > 0)
);

CREATE INDEX idx_turma_ano_letivo ON turma (ano_letivo);

CREATE TABLE turma_aluno (
    turma_id  BIGINT NOT NULL REFERENCES turma (id) ON DELETE CASCADE,
    aluno_id  BIGINT NOT NULL REFERENCES aluno (id) ON DELETE CASCADE,
    PRIMARY KEY (turma_id, aluno_id)
);

CREATE INDEX idx_turma_aluno_aluno ON turma_aluno (aluno_id);

CREATE TABLE turma_professor (
    turma_id      BIGINT NOT NULL REFERENCES turma (id) ON DELETE CASCADE,
    professor_id  BIGINT NOT NULL REFERENCES professor (id) ON DELETE CASCADE,
    PRIMARY KEY (turma_id, professor_id)
);

CREATE INDEX idx_turma_professor_professor ON turma_professor (professor_id);
