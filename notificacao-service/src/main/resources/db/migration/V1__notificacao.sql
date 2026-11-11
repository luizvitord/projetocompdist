-- Notificações geradas a partir dos eventos dos outros serviços.
-- O destinatário é o par (perfil, referência), o mesmo do JWT: a referência é o id do professor
-- (PROFESSOR) ou do aluno (ALUNO e RESPONSAVEL). ADMIN é a coordenação inteira e não tem referência.

CREATE TABLE notificacao (
    id                          BIGSERIAL     PRIMARY KEY,
    destinatario_perfil         VARCHAR(20)   NOT NULL,
    destinatario_referencia_id  BIGINT,
    tipo                        VARCHAR(30)   NOT NULL,
    titulo                      VARCHAR(100)  NOT NULL,
    mensagem                    VARCHAR(500)  NOT NULL,
    -- Rota do frontend relacionada (ex.: /alunos/12)
    link                        VARCHAR(200),
    criada_em                   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- NULL = não lida
    lida_em                     TIMESTAMPTZ,
    -- SHA-256 (hex) de tópico + chave + payload bruto do evento de origem
    evento_hash                 VARCHAR(64)   NOT NULL,
    CONSTRAINT ck_notificacao_perfil CHECK (destinatario_perfil IN ('ADMIN', 'PROFESSOR', 'ALUNO', 'RESPONSAVEL')),
    CONSTRAINT ck_notificacao_tipo CHECK (tipo IN ('NOTA_LANCADA', 'FALTA_REGISTRADA', 'MATRICULA_CRIADA',
        'MATRICULA_ALTERADA', 'ALUNO_CADASTRADO', 'PROFESSOR_ATRIBUIDO', 'TECNICO_CADASTRADO')),
    -- Só ADMIN fica sem referência
    CONSTRAINT ck_notificacao_referencia CHECK ((destinatario_perfil = 'ADMIN') = (destinatario_referencia_id IS NULL))
);

-- Idempotência: os eventos não têm id e o Kafka pode entregar o mesmo evento mais de uma vez. O
-- mesmo evento gera no máximo uma notificação por destinatário; a inserção usa ON CONFLICT DO NOTHING.
-- NULLS NOT DISTINCT: por padrão dois NULL são diferentes num índice único, e as notificações de
-- ADMIN (referência nula) nunca entrariam em conflito.
CREATE UNIQUE INDEX uk_notificacao_evento_destinatario
    ON notificacao (evento_hash, destinatario_perfil, destinatario_referencia_id) NULLS NOT DISTINCT;

-- Lista do destinatário, mais recentes primeiro
CREATE INDEX idx_notificacao_destinatario
    ON notificacao (destinatario_perfil, destinatario_referencia_id, criada_em DESC);

-- Contagem e lista das não lidas
CREATE INDEX idx_notificacao_nao_lidas
    ON notificacao (destinatario_perfil, destinatario_referencia_id) WHERE lida_em IS NULL;

-- Cache local de nomes, alimentado pelo evento schedule.updated do gestao-administrativa-service:
-- os eventos de notas, frequência e matrícula só trazem ids. Sem limite apertado de tamanho nem
-- CHECK: o domínio é do outro serviço, e um valor novo não pode travar o consumidor.

CREATE TABLE turma_nome (
    -- O turmaId do gestao-administrativa-service (sem sequence)
    turma_id          BIGINT        PRIMARY KEY,
    codigo            VARCHAR(255)  NOT NULL,
    nome              VARCHAR(255)  NOT NULL,
    -- occurredAt do último evento aplicado; eventos mais antigos são ignorados
    ultimo_evento_em  TIMESTAMPTZ   NOT NULL
);

CREATE TABLE disciplina_nome (
    -- O disciplinaId do gestao-administrativa-service (sem sequence)
    disciplina_id     BIGINT        PRIMARY KEY,
    nome              VARCHAR(255)  NOT NULL,
    ultimo_evento_em  TIMESTAMPTZ   NOT NULL
);
