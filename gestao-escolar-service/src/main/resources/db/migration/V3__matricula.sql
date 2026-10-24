-- Matrículas: vínculo de um aluno com uma turma em um ano letivo. Não são apagadas
-- (o cancelamento é uma mudança de status), então as FKs não têm ON DELETE.

CREATE TABLE matricula (
    id                   BIGSERIAL    PRIMARY KEY,
    aluno_id             BIGINT       NOT NULL REFERENCES aluno (id),
    turma_id             BIGINT       NOT NULL REFERENCES turma_replica (id),
    -- Copiado da turma no momento da matrícula
    ano_letivo           INTEGER      NOT NULL,
    status               VARCHAR(15)  NOT NULL,
    data_matricula       DATE         NOT NULL,
    data_encerramento    DATE,
    motivo               VARCHAR(500),
    -- Preenchido na matrícula criada por uma transferência
    matricula_origem_id  BIGINT       REFERENCES matricula (id),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_matricula_status CHECK (status IN ('ATIVA', 'TRANCADA', 'CANCELADA', 'CONCLUIDA', 'TRANSFERIDA')),
    CONSTRAINT ck_matricula_encerramento CHECK (data_encerramento IS NULL OR data_encerramento >= data_matricula)
);

-- No máximo uma matrícula vigente (ATIVA ou TRANCADA) por aluno e ano letivo
CREATE UNIQUE INDEX uk_matricula_aluno_ano_vigente ON matricula (aluno_id, ano_letivo)
    WHERE status IN ('ATIVA', 'TRANCADA');

CREATE INDEX idx_matricula_aluno ON matricula (aluno_id);
-- Contagem de vagas, filtro de alunos por turma e chamadas (matrículas ATIVA da turma)
CREATE INDEX idx_matricula_turma_status ON matricula (turma_id, status);
CREATE INDEX idx_matricula_ano_letivo ON matricula (ano_letivo);
CREATE INDEX idx_matricula_origem ON matricula (matricula_origem_id);
