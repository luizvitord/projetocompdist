-- Transactional outbox: os eventos são gravados na mesma transação da operação de negócio e
-- publicados no Kafka depois, pelo OutboxRelay. Um evento só some daqui depois de confirmado.

CREATE TABLE outbox_evento (
    id            BIGSERIAL     PRIMARY KEY,
    topico        VARCHAR(255)  NOT NULL,
    chave         VARCHAR(255)  NOT NULL,
    -- json (e não jsonb) guarda o texto exatamente como foi serializado: o jsonb reordena as chaves,
    -- e a mensagem enviada ao Kafka precisa ser byte a byte igual à serializada
    payload       JSON          NOT NULL,
    criado_em     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    publicado_em  TIMESTAMPTZ,
    tentativas    INTEGER       NOT NULL DEFAULT 0,
    ultimo_erro   TEXT,
    CONSTRAINT ck_outbox_evento_tentativas CHECK (tentativas >= 0)
);

-- Busca dos pendentes pelo relay, em ordem de gravação
CREATE INDEX idx_outbox_evento_pendentes ON outbox_evento (id) WHERE publicado_em IS NULL;
-- Limpeza dos já publicados
CREATE INDEX idx_outbox_evento_publicado_em ON outbox_evento (publicado_em) WHERE publicado_em IS NOT NULL;
