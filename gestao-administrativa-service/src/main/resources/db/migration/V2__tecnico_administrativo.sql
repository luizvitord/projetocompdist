-- Gestão de técnicos administrativos (funcionários que não dão aula)

CREATE TABLE tecnico_administrativo (
    id             BIGSERIAL    PRIMARY KEY,
    nome           VARCHAR(150) NOT NULL,
    cpf            VARCHAR(11)  NOT NULL,
    cargo          VARCHAR(30)  NOT NULL,
    setor          VARCHAR(100) NOT NULL,
    email          VARCHAR(150) NOT NULL,
    telefone       VARCHAR(30),
    data_admissao  DATE         NOT NULL,
    ativo          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_tecnico_cpf UNIQUE (cpf),
    CONSTRAINT ck_tecnico_cargo CHECK (cargo IN ('SECRETARIO', 'COORDENADOR', 'BIBLIOTECARIO',
                                                 'TECNICO_TI', 'AUXILIAR_ADMINISTRATIVO', 'OUTRO'))
);

-- Email único sem diferenciar maiúsculas/minúsculas
CREATE UNIQUE INDEX uk_tecnico_email ON tecnico_administrativo (lower(email));
CREATE INDEX idx_tecnico_nome ON tecnico_administrativo (lower(nome));
