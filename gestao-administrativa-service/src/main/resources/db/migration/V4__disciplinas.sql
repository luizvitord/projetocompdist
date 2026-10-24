-- Disciplinas e oferta de disciplinas por turma

CREATE TABLE disciplina (
    id             BIGSERIAL    PRIMARY KEY,
    codigo         VARCHAR(20)  NOT NULL,
    nome           VARCHAR(100) NOT NULL,
    carga_horaria  INTEGER,
    ativa          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_disciplina_carga_horaria CHECK (carga_horaria IS NULL OR carga_horaria > 0)
);

-- Código único sem diferenciar maiúsculas/minúsculas
CREATE UNIQUE INDEX uk_disciplina_codigo ON disciplina (lower(codigo));
CREATE INDEX idx_disciplina_nome ON disciplina (lower(nome));

CREATE TABLE turma_disciplina (
    id             BIGSERIAL    PRIMARY KEY,
    turma_id       BIGINT       NOT NULL REFERENCES turma (id) ON DELETE CASCADE,
    disciplina_id  BIGINT       NOT NULL REFERENCES disciplina (id),
    professor_id   BIGINT       REFERENCES professor (id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_turma_disciplina UNIQUE (turma_id, disciplina_id)
);

-- turma_id já é coberta pelo índice da chave única (turma_id, disciplina_id)
CREATE INDEX idx_turma_disciplina_disciplina ON turma_disciplina (disciplina_id);
CREATE INDEX idx_turma_disciplina_professor ON turma_disciplina (professor_id);
