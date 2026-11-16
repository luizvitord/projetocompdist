-- Permite revogar tokens emitidos antes de alterações de credencial, perfil ou vínculo.
ALTER TABLE usuario
    ADD COLUMN versao_sessao BIGINT NOT NULL DEFAULT 1;
