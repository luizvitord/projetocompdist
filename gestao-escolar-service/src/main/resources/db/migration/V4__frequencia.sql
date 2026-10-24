-- Frequências: chamada de uma turma em uma disciplina em uma data, com um registro por matrícula

CREATE TABLE chamada (
    id             BIGSERIAL    PRIMARY KEY,
    turma_id       BIGINT       NOT NULL,
    disciplina_id  BIGINT       NOT NULL,
    data           DATE         NOT NULL,
    -- Só informativo (sem validação de permissão por enquanto)
    professor_id   BIGINT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_chamada_oferta FOREIGN KEY (turma_id, disciplina_id)
        REFERENCES turma_disciplina_replica (turma_id, disciplina_id),
    CONSTRAINT uk_chamada_turma_disciplina_data UNIQUE (turma_id, disciplina_id, data)
);

-- (turma_id, disciplina_id, data) já é coberta pelo índice da chave única
CREATE INDEX idx_chamada_disciplina ON chamada (disciplina_id);
CREATE INDEX idx_chamada_data ON chamada (data);

CREATE TABLE registro_frequencia (
    id            BIGSERIAL    PRIMARY KEY,
    chamada_id    BIGINT       NOT NULL REFERENCES chamada (id),
    matricula_id  BIGINT       NOT NULL REFERENCES matricula (id),
    status        VARCHAR(10)  NOT NULL,
    observacao    VARCHAR(255),
    CONSTRAINT uk_registro_frequencia_chamada_matricula UNIQUE (chamada_id, matricula_id),
    CONSTRAINT ck_registro_frequencia_status CHECK (status IN ('PRESENTE', 'FALTA', 'ATRASO'))
);

-- chamada_id já é coberta pelo índice da chave única (chamada_id, matricula_id)
CREATE INDEX idx_registro_frequencia_matricula ON registro_frequencia (matricula_id);
