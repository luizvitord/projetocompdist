-- Avaliações, notas e histórico de correções

CREATE TABLE avaliacao (
    id             BIGSERIAL     PRIMARY KEY,
    turma_id       BIGINT        NOT NULL,
    disciplina_id  BIGINT        NOT NULL,
    bimestre       INTEGER       NOT NULL,
    tipo           VARCHAR(15)   NOT NULL,
    descricao      VARCHAR(255),
    peso           NUMERIC(5, 2) NOT NULL DEFAULT 1,
    nota_maxima    NUMERIC(5, 2) NOT NULL DEFAULT 10,
    data           DATE          NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_avaliacao_oferta FOREIGN KEY (turma_id, disciplina_id)
        REFERENCES turma_disciplina_replica (turma_id, disciplina_id),
    CONSTRAINT ck_avaliacao_bimestre CHECK (bimestre BETWEEN 1 AND 4),
    CONSTRAINT ck_avaliacao_tipo CHECK (tipo IN ('PROVA', 'TRABALHO', 'ATIVIDADE', 'RECUPERACAO')),
    CONSTRAINT ck_avaliacao_peso CHECK (peso > 0),
    CONSTRAINT ck_avaliacao_nota_maxima CHECK (nota_maxima > 0)
);

-- No máximo uma recuperação por turma, disciplina e bimestre
CREATE UNIQUE INDEX uk_avaliacao_recuperacao ON avaliacao (turma_id, disciplina_id, bimestre)
    WHERE tipo = 'RECUPERACAO';
CREATE INDEX idx_avaliacao_turma_disciplina_bimestre ON avaliacao (turma_id, disciplina_id, bimestre);
CREATE INDEX idx_avaliacao_disciplina ON avaliacao (disciplina_id);

CREATE TABLE nota (
    id            BIGSERIAL     PRIMARY KEY,
    avaliacao_id  BIGINT        NOT NULL REFERENCES avaliacao (id),
    matricula_id  BIGINT        NOT NULL REFERENCES matricula (id),
    -- O limite superior (nota_maxima da avaliação) é validado no service
    valor         NUMERIC(5, 2) NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_nota_avaliacao_matricula UNIQUE (avaliacao_id, matricula_id),
    CONSTRAINT ck_nota_valor CHECK (valor >= 0)
);

-- avaliacao_id já é coberta pelo índice da chave única (avaliacao_id, matricula_id)
CREATE INDEX idx_nota_matricula ON nota (matricula_id);

CREATE TABLE nota_historico (
    id              BIGSERIAL     PRIMARY KEY,
    nota_id         BIGINT        NOT NULL REFERENCES nota (id),
    valor_anterior  NUMERIC(5, 2) NOT NULL,
    valor_novo      NUMERIC(5, 2) NOT NULL,
    motivo          VARCHAR(500)  NOT NULL,
    alterado_em     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_nota_historico_valores CHECK (valor_anterior >= 0 AND valor_novo >= 0)
);

CREATE INDEX idx_nota_historico_nota ON nota_historico (nota_id);

-- O histórico de correções nunca é alterado nem apagado, nem por acesso direto ao banco
CREATE FUNCTION nota_historico_somente_insercao() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'nota_historico não pode ser alterado nem apagado';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_nota_historico_somente_insercao
    BEFORE UPDATE OR DELETE ON nota_historico
    FOR EACH ROW EXECUTE FUNCTION nota_historico_somente_insercao();
