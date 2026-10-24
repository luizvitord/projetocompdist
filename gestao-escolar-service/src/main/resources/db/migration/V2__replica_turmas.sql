-- Cópia local (somente leitura) das turmas e das disciplinas ofertadas, que pertencem ao
-- gestao-administrativa-service. Alimentada pelo evento schedule.updated e, como fallback,
-- pelo GET /api/turmas/{id} do outro serviço.
-- Linhas nunca são apagadas, só marcadas como removidas: matrículas, chamadas e avaliações
-- antigas continuam apontando para elas.

CREATE TABLE turma_replica (
    -- O turmaId do gestao-administrativa-service (sem sequence)
    id                BIGINT       PRIMARY KEY,
    codigo            VARCHAR(30)  NOT NULL,
    nome              VARCHAR(100) NOT NULL,
    ano_letivo        INTEGER      NOT NULL,
    -- Sem CHECK: o domínio do turno é do outro serviço, e um valor novo não pode travar o consumidor
    turno             VARCHAR(20)  NOT NULL,
    capacidade        INTEGER,
    removida          BOOLEAN      NOT NULL DEFAULT FALSE,
    -- occurredAt do último evento aplicado; NULL quando a linha veio do fallback síncrono
    ultimo_evento_em  TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_turma_replica_capacidade CHECK (capacidade IS NULL OR capacidade > 0)
);

CREATE INDEX idx_turma_replica_ano_letivo ON turma_replica (ano_letivo);

CREATE TABLE turma_disciplina_replica (
    turma_id       BIGINT       NOT NULL REFERENCES turma_replica (id),
    disciplina_id  BIGINT       NOT NULL,
    codigo         VARCHAR(20)  NOT NULL,
    nome           VARCHAR(100) NOT NULL,
    professor_id   BIGINT,
    removida       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (turma_id, disciplina_id)
);

-- turma_id já é coberta pelo índice da chave primária (turma_id, disciplina_id)
CREATE INDEX idx_turma_disciplina_replica_disciplina ON turma_disciplina_replica (disciplina_id);
