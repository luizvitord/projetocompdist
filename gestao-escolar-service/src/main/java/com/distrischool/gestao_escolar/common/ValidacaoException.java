package com.distrischool.gestao_escolar.common;

import java.util.Map;

/**
 * Dados inválidos detectados pelo service (regras que dependem do estado, e não só do corpo
 * da requisição). Vira 400; {@code propriedades} vão para o ProblemDetail.
 */
public class ValidacaoException extends RuntimeException {

    private final transient Map<String, Object> propriedades;

    public ValidacaoException(String message, Map<String, Object> propriedades) {
        super(message);
        this.propriedades = propriedades;
    }

    /** Erro em um campo, no mesmo formato ({@code erros}) da validação do corpo. */
    public static ValidacaoException campo(String campo, String mensagem) {
        return new ValidacaoException("Dados inválidos", Map.of("erros", Map.of(campo, mensagem)));
    }

    public Map<String, Object> getPropriedades() {
        return propriedades;
    }
}
